/**
 * VStar: a statistical analysis tool for variable star data.
 * Copyright (C) 2010  AAVSO (http://www.aavso.org/)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */
package org.aavso.tools.vstar.vela;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aavso.tools.vstar.scripting.VStarScriptingAPI;
import org.aavso.tools.vstar.ui.VStar;
import org.aavso.tools.vstar.util.Pair;
import org.aavso.tools.vstar.util.date.AbstractDateUtil;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * VeLa: VStar expression Language interpreter
 */
public class VeLaInterpreter {

    private boolean verbose;

    private List<File> sourceDirectories;

    private Stack<Operand> stack;

    private Stack<VeLaEnvironment<Operand>> environments;

    // AST and result caches.
    private static Map<String, AST> exprToAST = new HashMap<String, AST>();

    // Regular expression pattern cache.
    private static Map<String, Pattern> regexPatterns = new HashMap<String, Pattern>();

    private static List<FunctionExecutor> javaClassFunctionExecutors = null;

    private VeLaErrorListener errorListener;

    /**
     * Construct a VeLa interpreter with an initial scope and intrinsic functions.
     * 
     * @param verbose           Verbose mode?
     * @param addVStarAPI       Add the VStar API?
     * @param sourceDirectories A list of source directories containing VeLa source
     *                          files (ending in ".vl" or ".vela") to be loaded.
     */
    public VeLaInterpreter(boolean verbose, boolean addVStarAPI, List<File> sourceDirectories) {
        this.verbose = verbose;
        this.sourceDirectories = sourceDirectories;

        errorListener = new VeLaErrorListener();

        stack = new Stack<Operand>();
        environments = new Stack<VeLaEnvironment<Operand>>();

        environments.push(new VeLaScope());

        // Collect functions from reflection over Java classes
        Set<Class<?>> permittedTypes = new HashSet<Class<?>>();
        permittedTypes.add(int.class);
        permittedTypes.add(double.class);
        permittedTypes.add(boolean.class);
        permittedTypes.add(String.class);
        permittedTypes.add(CharSequence.class);
        permittedTypes.add(void.class);
        permittedTypes.add(Type.DBL_ARR.getClass());
        permittedTypes.add(Type.DBL_CLASS_ARR.getClass());
        if (addVStarAPI) {
            // TODO: really needed here?
            permittedTypes.add(VStarScriptingAPI.class);
        }

        if (javaClassFunctionExecutors == null) {
            javaClassFunctionExecutors = new ArrayList<FunctionExecutor>();
            addFunctionExecutorsFromClass(Math.class, null, permittedTypes, Collections.emptySet());

            addFunctionExecutorsFromClass(String.class, null, permittedTypes,
                    new HashSet<String>(Arrays.asList("JOIN", "FORMAT")));

            if (addVStarAPI) {
                addFunctionExecutorsFromClass(VStarScriptingAPI.class, VStarScriptingAPI.getInstance(), permittedTypes,
                        Collections.emptySet());
            }
        }

        initBindings();
        initFunctionExecutors();
        // allows user to override intrinsic code
        loadUserCode();
    }

    /**
     * Construct a VeLa interpreter with verbose mode as specified, adding the VeLa
     * API, and no source directories.
     * 
     * @param verbose Verbose mode?
     */
    public VeLaInterpreter(boolean verbose) {
        this(verbose, true, Collections.emptyList());
    }

    /**
     * Construct a VeLa interpreter with verbose mode set to false, adding the VeLa
     * API, and no source directories.
     */
    public VeLaInterpreter() {
        this(false, true, Collections.emptyList());
    }

    /**
     * @param verbose the verbose to set
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Push an environment onto the stack.
     * 
     * @param environment The environment to be pushed.
     */
    public void pushEnvironment(VeLaEnvironment<Operand> environment) {
        this.environments.push(environment);
    }

    /**
     * Pop the top-most environment from the stack and return it.
     * 
     * @return The top-most environment.
     */
    public VeLaEnvironment<Operand> popEnvironment() {
        return this.environments.pop();
    }

    /**
     * Peek the top-most environment on the stack and return it.
     * 
     * @return The top-most environment.
     */
    public VeLaEnvironment<Operand> peekEnvironment() {
        return this.environments.peek();
    }

    /**
     * @return The stack of environments
     */
    public Stack<VeLaEnvironment<Operand>> getEnvironments() {
        return environments;
    }

    /**
     * Return all scopes (activation records) on the stack as a list in order from
     * oldest to newest.
     */
    public List<VeLaScope> getScopes() {
        List<VeLaScope> scopes = new ArrayList<VeLaScope>();

        for (VeLaEnvironment<Operand> env : environments) {
            if (env instanceof VeLaScope) {
                scopes.add((VeLaScope) env);
            }
        }

        return scopes;
    }

    /**
     * @return the operand stack
     */
    public Stack<Operand> getStack() {
        return stack;
    }

    /**
     * Pop and return an operand from the stack if not empty.
     * 
     * @param msgOnError The exception message to use if the stack is empty.
     * @return The operand on top of the stack.
     * @throws VeLaEvalError Thrown when the stack is empty.
     */
    public Operand pop(String msgOnError) throws VeLaEvalError {
        if (!stack.isEmpty()) {
            return stack.pop();
        } else {
            throw new VeLaEvalError(msgOnError);
        }
    }

    /**
     * VeLa program interpreter entry point.
     * 
     * @param file A path to a file containing a VeLa program string to be
     *             interpreted.
     * @return An optional result, depending upon whether a value was left on the
     *         stack.
     * @throws VeLaParseError If a parse error occurs.
     * @throws VeLaEvalError  If an evaluation error occurs.
     */
    public Optional<Operand> program(File path) throws VeLaParseError, VeLaEvalError {
        StringBuffer code = new StringBuffer();

        try {
            try (Stream<String> stream = Files.lines(Paths.get(path.getAbsolutePath()))) {
                stream.forEachOrdered(line -> {
                    code.append(line);
                    code.append("\n");
                });
            }
        } catch (IOException e) {
            throw new VeLaEvalError("Error when attempting to read VeLa file " + path.getAbsolutePath());
        }

        return program(code.toString());
    }

    /**
     * VeLa program interpreter entry point.
     * 
     * @param prog The VeLa program string to be interpreted.
     * @return An optional result, depending upon whether a value was left on the
     *         stack.
     * @throws VeLaParseError If a parse error occurs.
     * @throws VeLaEvalError  If an evaluation error occurs.
     */
    public Optional<Operand> program(String prog) throws VeLaParseError, VeLaEvalError {
        return veLaToResultASTPair(prog).first;
    }

    /**
     * VeLa program interpreter entry point.
     * 
     * @param prog The VeLa program string to be interpreted.
     * @return A pair consisting of an optional result, depending upon whether a
     *         value was left on the stack, and the AST that gave rise to the
     *         result.
     * @throws VeLaParseError If a parse error occurs.
     * @throws VeLaEvalError  If an evaluation error occurs.
     */
    public Pair<Optional<Operand>, AST> veLaToResultASTPair(String prog) throws VeLaParseError, VeLaEvalError {
        VeLaParser.SequenceContext tree = getParser(prog).sequence();
        return commonInterpreter(prog, tree);
    }

    /**
     * Expression interpreter entry point.
     * 
     * @param expr The expression string to be interpreted.
     * @return An operand.
     * @throws VeLaParseError If a parse error occurs.
     * @throws VeLaEvalError  If an evaluation error occurs.
     */
//    public double expression(String expr) throws VeLaEvalError {
//
//        // TODO: why?
//
//        VeLaParser.AdditiveExpressionContext tree = getParser(expr).additiveExpression();
//
//        Optional<Operand> result = commonInterpreter(expr, tree).first;
//
//        if (result.isPresent()) {
//            if (result.get().getType() == Type.REAL) {
//                return (double) result.get().doubleVal();
//            } else if (result.get().getType() == Type.INTEGER) {
//                return result.get().intVal();
//            } else {
//                throw new VeLaEvalError("Numeric value expected as result");
//            }
//        } else {
//            throw new VeLaEvalError("Numeric value expected as result");
//        }
//    }

    /**
     * Expression interpreter entry point.
     * 
     * @param expr The expression string to be interpreted.
     * @return An operand.
     * @throws VeLaParseError If a parse error occurs.
     * @throws VeLaEvalError  If an evaluation error occurs.
     */
    public Operand expressionToOperand(String expr) throws VeLaParseError, VeLaEvalError {

        // TODO: why?

        VeLaParser.ExpressionContext tree = getParser(expr).expression();

        Optional<Operand> result = commonInterpreter(expr, tree).first;

        if (result.isPresent()) {
            return result.get();
        } else {
            throw new VeLaEvalError("Result expected");
        }
    }

    /**
     * VeLa boolean expression interpreter entry point.
     * 
     * @param expr The VeLa expression string to be interpreted.
     * @return A Boolean value result.
     * @throws VeLaParseError If a parse error occurs.
     * @throws VeLaEvalError  If an evaluation error occurs.
     * @deprecated Only used in test code: remove!
     */
    public boolean booleanExpression(String expr) throws VeLaParseError, VeLaEvalError {

        VeLaParser.BooleanExpressionContext tree = getParser(expr).booleanExpression();

        Optional<Operand> result = commonInterpreter(expr, tree).first;

        if (result.isPresent()) {
            return result.get().booleanVal();
        } else {
            throw new VeLaEvalError("Numeric value expected as result");
        }
    }

    // Helpers

    /**
     * Given an expression string, return a VeLa parser object.
     * 
     * @param expr The expression string.
     * @return The parser object.
     */
    private VeLaParser getParser(String expr) {
        CharStream stream = new ANTLRInputStream(expr);

        VeLaLexer lexer = new VeLaLexer(stream);
        lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
        lexer.addErrorListener(errorListener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        VeLaParser parser = new VeLaParser(tokens);
        parser.addErrorListener(errorListener);

        return parser;
    }

    /**
     * Common VeLa evaluation entry point. This will be most effective when prog is
     * an often used expression.
     * 
     * @param prog The VeLa program string to be interpreted.
     * @param tree The result of parsing the VeLa expression.
     * @return An optional result depending upon whether a value is left on the
     *         stack and the AST that was constructed and evaluated.
     * @throws VeLaEvalError If an evaluation error occurs.
     */
    public Pair<Optional<Operand>, AST> commonInterpreter(String prog, ParserRuleContext tree) throws VeLaEvalError {

        Optional<Operand> result = Optional.empty();

        AST ast = commonParseTreeWalker(prog, tree);

        if (ast != null) {
            // Evaluate the abstract syntax tree and cache the result.
            eval(ast);
            if (!stack.isEmpty()) {
                result = Optional.of(stack.pop());
            } else {
                result = Optional.empty();
            }
        }

        return new Pair<Optional<Operand>, AST>(result, ast);
    }

    /**
     * Common parse tree walker and AST generator.
     * 
     * @param prog The VeLa program to be interpreted.
     * @param tree The parse tree resulting from parsing the VeLa expression.
     * @return The abstract syntax tree created by walking the parse tree.
     * @throws VeLaParseError If a parse error occurs.
     */
    protected AST commonParseTreeWalker(String prog, ParserRuleContext tree) throws VeLaParseError {

        AST ast = null;

        // Remove whitespace and change to uppercase to ensure a canonical
        // expression string for caching purposes.
        prog = prog.replace(" ", "").replace("\t", "").toUpperCase();

        // We cache abstract syntax trees by top-level program string
        // to improve performance.
        // boolean astCached = false;
        if (exprToAST.containsKey(prog)) {
            ast = exprToAST.get(prog);
            // astCached = true;
        } else {
            ExpressionVisitor visitor = new ExpressionVisitor(this);
            ast = visitor.visit(tree);

            if (ast != null) {
                // This relates a VeLa program or expression to an AST.
                exprToAST.put(prog, ast);
            }
        }

//		if (verbose && ast != null) {
//			if (astCached) {
//				System.out.println(String.format("%s [AST cached]", ast));
//			} else {
//				System.out.println(ast);
//			}
//		}

        return ast;
    }

    /**
     * <p>
     * Given an AST representing a VeLa program, interpret this via a depth first
     * traversal, leaving the result of evaluation on the stack.
     * </p>
     * <p>
     * The name "eval" is used in deference to John McCarthy's Lisp and its eval
     * function, the equivalent of Maxwell's equations in Computer Science.
     * </p>
     * <p>
     * I've also just noticed that VeLa is an anagram of eval! :)
     * </p>
     * 
     * @param ast An abstract syntax tree.
     * @throws VeLaEvalError If an evaluation error occurs.
     */
    public void eval(AST ast) throws VeLaEvalError {
        if (ast.isLiteral()) {
            stack.push(ast.getOperand());
        } else {
            Operation op = ast.getOp();

            if (op.arity() == 2) {
                // Binary
                eval(ast.left());
                eval(ast.right());

                applyBinaryOperation(op);

            } else if (op.arity() == 1) {
                // Unary
                eval(ast.head());

                Operand operand = stack.pop();

                switch (op) {
                case NEG:
                    switch (operand.getType()) {
                    case INTEGER:
                        stack.push(new Operand(Type.INTEGER, -operand.intVal()));
                        break;
                    case REAL:
                        stack.push(new Operand(Type.REAL, -operand.doubleVal()));
                        break;
                    case LIST:
                        List<Operand> negResult = new ArrayList<Operand>();
                        for (int i = 0; i < operand.listVal().size(); i++) {
                            Operand scalar = operand.listVal().get(i);
                            switch (scalar.getType()) {
                            case INTEGER:
                                negResult.add(new Operand(Type.INTEGER, -scalar.intVal()));
                                break;
                            case REAL:
                                negResult.add(new Operand(Type.REAL, -scalar.doubleVal()));
                                break;
                            default:
                                binaryOpError(op, Type.INTEGER, Type.REAL);
                                break;
                            }
                        }
                        stack.push(new Operand(Type.LIST, negResult));
                        break;
                    default:
                        binaryOpError(op, Type.INTEGER, Type.REAL, Type.LIST);
                        break;
                    }
                    break;
                case NOT:
                    switch (operand.getType()) {
                    case BOOLEAN:
                        stack.push(new Operand(Type.BOOLEAN, !operand.booleanVal()));
                        break;
                    case INTEGER:
                        stack.push(new Operand(Type.INTEGER, ~operand.intVal()));
                        break;
                    case LIST:
                        List<Operand> notResult = new ArrayList<Operand>();
                        for (int i = 0; i < operand.listVal().size(); i++) {
                            Operand scalar = operand.listVal().get(i);
                            switch (scalar.getType()) {
                            case BOOLEAN:
                                notResult.add(new Operand(Type.BOOLEAN, !scalar.booleanVal()));
                                break;
                            case INTEGER:
                                notResult.add(new Operand(Type.INTEGER, ~scalar.intVal()));
                                break;
                            default:
                                binaryOpError(op, Type.INTEGER, Type.BOOLEAN);
                                break;
                            }
                        }
                        stack.push(new Operand(Type.LIST, notResult));
                        break;
                    default:
                        binaryOpError(op, Type.INTEGER, Type.BOOLEAN, Type.LIST);
                        ;
                        break;
                    }
                }
            } else if (ast.getOp() == Operation.SYMBOL) {
                // Look up variable or function in the environment stack,
                // pushing it onto the operand stack if it exists, looking for
                // and evaluating a function if not, throwing an exception
                // otherwise.
                String name = ast.getToken().toUpperCase();
                // Bound symbol?
                Optional<Operand> result = lookupBinding(name);
                if (result.isPresent()) {
                    stack.push(result.get());
                } else {
                    // Function?
                    Optional<List<FunctionExecutor>> funList = lookupFunctions(name);
                    if (funList.isPresent()) {
                        // The first function in the list is chosen in the
                        // absence of parameter type information.
                        stack.push(new Operand(Type.FUNCTION, funList.get().get(0)));
                    } else {
                        throw new VeLaEvalError("Unknown binding \"" + ast.getToken() + "\"");
                    }
                }
            } else if (ast.getOp() == Operation.LIST) {
                // Evaluate list elements.
                List<Operand> elements = new ArrayList<Operand>();

                if (ast.hasChildren()) {
                    for (int i = ast.getChildren().size() - 1; i >= 0; i--) {
                        eval(ast.getChildren().get(i));
                    }

                    // Create and push list of operands.
                    for (int i = 1; i <= ast.getChildren().size(); i++) {
                        elements.add(stack.pop());
                    }
                }

                stack.push(new Operand(Type.LIST, elements));
            } else if (ast.getOp().isSpecialForm()) {
                specialForm(ast);
            }
        }
    }

    /**
     * Handle special forms.
     * 
     * @param ast The special form's AST.
     */
    private void specialForm(AST ast) {
        switch (ast.getOp()) {
        case SEQUENCE:
            // Evaluate each child AST in turn. No children means an empty
            // program or one consisting only of whitespace or comments.
            if (ast.hasChildren()) {
                for (AST child : ast.getChildren()) {
                    eval(child);
                }
            }
            break;

        case BIND:
        case IS:
            eval(ast.right());
            String varName = ast.left().getToken();
            String msg = "No value to bind to \"" + varName + "\"";
            bind(varName, pop(msg), ast.getOp() == Operation.IS);
            break;

        case FUNDEF:
            // Does this function have a name or is it anonymous?
            Optional<String> name = Optional.empty();
            if (ast.head().getOp() == Operation.SYMBOL) {
                name = Optional.of(ast.head().getToken());
            }

            // Extract components from AST in order to create a function
            // executor.
            Optional<String> helpString = Optional.empty();
            List<String> parameterNames = new ArrayList<String>();
            List<Type> parameterTypes = new ArrayList<Type>();
            Optional<Type> returnType = Optional.empty();
            Optional<AST> functionBody = Optional.empty();

            for (int i = name.isPresent() ? 1 : 0; i < ast.getChildren().size(); i++) {
                AST child = ast.getChildren().get(i);
                switch (child.getOp()) {
                case HELP_COMMENT:
                    String help = child.getToken();
                    help = help.replace("<<", "").replace(">>", "").trim();
                    helpString = Optional.of(help);
                    break;

                case PAIR:
                    if (child.hasChildren()) {
                        parameterNames.add(child.left().getToken());
                        parameterTypes.add(Type.name2Vela(child.right().getToken()));
                    }
                    break;

                case SYMBOL:
                    returnType = Optional.of(Type.name2Vela(child.getToken()));
                    break;

                case SEQUENCE:
                    functionBody = Optional.of(child);
                    break;

                default:
                    break;
                }
            }

            // Add the named function to the top-most scope's function namespace
            // or the push the anonymous function to the operand stack.
            UserDefinedFunctionExecutor function = new UserDefinedFunctionExecutor(this, name, parameterNames,
                    parameterTypes, returnType, functionBody, helpString);

            if (name.isPresent()) {
                addFunctionExecutor(function);
            } else {
                stack.push(new Operand(Type.FUNCTION, function));
            }

            break;

        case FUNCALL:
            List<Operand> params = new ArrayList<Operand>();

            FunctionExecutor anon = null;

            int childLimit = 1;

            for (int i = ast.getChildren().size() - 1; i >= childLimit; i--) {
                eval(ast.getChildren().get(i));
            }

            // Prepare actual parameter list.
            for (int i = childLimit; i <= ast.getChildren().size() - 1; i++) {
                Operand value = stack.pop();
                params.add(value);
            }

            if (ast.head().getOp() == Operation.SYMBOL) {
                applyFunction(ast.head().getToken(), params);
            } else {
                eval(ast.head());
                anon = stack.pop().functionVal();

                if (!applyFunction(anon, params)) {
                    throw new VeLaEvalError("Invalid parameters for function \"" + anon + "\"");
                }
            }

            break;

        case WHEN:
            // Evaluate each antecedent in turn, pushing the value
            // of the first consequent whose antecedent is true and stop
            // antecedent evaluation.
            for (AST pair : ast.getChildren()) {
                eval(pair.left());
                if (stack.pop().booleanVal()) {
                    eval(pair.right());
                    break;
                }
            }
            break;

        case IF:
            // Evaluate the antecedent, pushing the value of the first
            // consequent if the condition is true, and the value of the
            // second consequent otherwise.
            eval(ast.head());
            if (stack.pop().booleanVal()) {
                eval(ast.getChildren().get(1));
            } else if (ast.getChildren().size() == 3) {
                eval(ast.getChildren().get(2));
            }
            break;

        case WHILE:
            // Evaluate the condition, executing the body while it is true.
            while (true) {
                eval(ast.left());
                if (!stack.isEmpty() && stack.peek().getType() == Type.BOOLEAN && stack.pop().booleanVal()) {
                    eval(ast.right());
                } else {
                    break;
                }
            }
            break;

        default:
            break;
        }
    }

    /**
     * Apply a binary operation to the values on the stack, consuming them and
     * leaving a result on the stack.
     * 
     * @param op The operation to be applied.
     */
    private void applyBinaryOperation(Operation op) {
        Operand operand2 = stack.pop();
        Operand operand1 = stack.pop();

        if (operand1.getType() == Type.LIST || operand2.getType() == Type.LIST) {
            applyBinaryListOperation(op, operand1, operand2);
        } else {
            // TODO Refactor to N methods or define functions for each in
            // Operation/Operand or use lambda for n+m, n-m ...
            // =>
            // https://stackoverflow.com/questions/13604703/how-do-i-define-a-method-which-takes-a-lambda-as-a-parameter-in-java-8
            // type unification is not relevant to all operations, e.g. IN;

            // Unify the operand types if possible.
            Pair<Operand, Operand> operands = unifyTypes(operand1, operand2);
            operand1 = operands.first;
            operand2 = operands.second;

            // Arbitrarily use the type of the first operand.
            Type type = operands.first.getType();

            switch (op) {
            case ADD:
                switch (type) {
                case INTEGER:
                    stack.push(new Operand(Type.INTEGER, operand1.intVal() + operand2.intVal()));
                    break;
                case REAL:
                    stack.push(new Operand(Type.REAL, operand1.doubleVal() + operand2.doubleVal()));
                    break;
                case STRING:
                    stack.push(new Operand(Type.STRING, operand1.stringVal() + operand2.stringVal()));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER, Type.REAL, Type.STRING);
                    break;
                }
                break;
            case SUB:
                switch (type) {
                case INTEGER:
                    stack.push(new Operand(Type.INTEGER, operand1.intVal() - operand2.intVal()));
                    break;
                case REAL:
                    stack.push(new Operand(Type.REAL, operand1.doubleVal() - operand2.doubleVal()));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER, Type.REAL);
                    break;
                }
                break;
            case MUL:
                switch (type) {
                case INTEGER:
                    stack.push(new Operand(Type.INTEGER, operand1.intVal() * operand2.intVal()));
                    break;
                case REAL:
                    stack.push(new Operand(Type.REAL, operand1.doubleVal() * operand2.doubleVal()));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER, Type.REAL);
                    break;
                }
                break;
            case DIV:
                switch (type) {
                case INTEGER:
                    if (operand2.intVal() != 0) {
                        stack.push(new Operand(Type.INTEGER, operand1.intVal() / operand2.intVal()));
                    } else {
                        throw new VeLaEvalError(
                                String.format("%s/%s: division by zero error", operand1.intVal(), operand2.intVal()));
                    }
                    break;
                case REAL:
                    Double result = operand1.doubleVal() / operand2.doubleVal();
                    if (!result.isInfinite()) {
                        stack.push(new Operand(Type.REAL, result));
                    } else {
                        throw new VeLaEvalError(String.format("%s/%s: division by zero error", operand1.doubleVal(),
                                operand2.doubleVal()));
                    }
                    break;
                default:
                    binaryOpError(op, Type.INTEGER, Type.REAL);
                    break;
                }
                break;
            case POW:
                switch (type) {
                case INTEGER:
                    long base = operand1.intVal();
                    long result = base;
                    long exponent = operand2.intVal();
                    if (exponent == 0) {
                        result = 1;
                    } else {
                        // multiply operand1 by itself n-1 times
                        for (int i = 1; i <= exponent - 1; i++) {
                            result *= base;
                        }
                    }
                    stack.push(new Operand(Type.INTEGER, result));
                    break;
                case REAL:
                    stack.push(new Operand(Type.REAL, Math.pow(operand1.doubleVal(), operand2.doubleVal())));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER, Type.REAL);
                    break;
                }
                break;
            case AND:
                switch (type) {
                case BOOLEAN:
                    stack.push(new Operand(Type.BOOLEAN, operand1.booleanVal() & operand2.booleanVal()));
                    break;
                case INTEGER:
                    stack.push(new Operand(Type.INTEGER, operand1.intVal() & operand2.intVal()));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER, Type.BOOLEAN);
                    break;
                }
                break;
            case XOR:
                switch (type) {
                case BOOLEAN:
                    stack.push(new Operand(Type.BOOLEAN, operand1.booleanVal() ^ operand2.booleanVal()));
                    break;
                case INTEGER:
                    stack.push(new Operand(Type.INTEGER, operand1.intVal() ^ operand2.intVal()));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER, Type.BOOLEAN);
                    break;
                }
                break;
            case OR:
                switch (type) {
                case BOOLEAN:
                    stack.push(new Operand(Type.BOOLEAN, operand1.booleanVal() | operand2.booleanVal()));
                    break;
                case INTEGER:
                    stack.push(new Operand(Type.INTEGER, operand1.intVal() | operand2.intVal()));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER, Type.BOOLEAN);
                    break;
                }
                break;
            case EQUAL:
                switch (type) {
                case BOOLEAN:
                    stack.push(new Operand(Type.BOOLEAN, operand1.booleanVal() == operand2.booleanVal()));
                    break;
                case INTEGER:
                    stack.push(new Operand(Type.BOOLEAN, operand1.intVal() == operand2.intVal()));
                    break;
                case REAL:
                    stack.push(new Operand(Type.BOOLEAN, operand1.doubleVal() == operand2.doubleVal()));
                    break;
                case STRING:
                    stack.push(new Operand(Type.BOOLEAN, operand1.stringVal().equals(operand2.stringVal())));
                    break;
                default:
                    binaryOpError(op, Type.BOOLEAN, Type.INTEGER, Type.REAL, Type.STRING);
                    break;
                }
                break;
            case NOT_EQUAL:
                switch (type) {
                case BOOLEAN:
                    stack.push(new Operand(Type.BOOLEAN, operand1.booleanVal() != operand2.booleanVal()));
                    break;
                case INTEGER:
                    stack.push(new Operand(Type.BOOLEAN, operand1.intVal() != operand2.intVal()));
                    break;
                case REAL:
                    stack.push(new Operand(Type.BOOLEAN, operand1.doubleVal() != operand2.doubleVal()));
                    break;
                case STRING:
                    stack.push(new Operand(Type.BOOLEAN, !operand1.stringVal().equals(operand2.stringVal())));
                    break;
                default:
                    binaryOpError(op, Type.BOOLEAN, Type.INTEGER, Type.REAL, Type.STRING);
                    break;
                }
                break;
            case GREATER_THAN:
                switch (type) {
                case INTEGER:
                    stack.push(new Operand(Type.BOOLEAN, operand1.intVal() > operand2.intVal()));
                    break;
                case REAL:
                    stack.push(new Operand(Type.BOOLEAN, operand1.doubleVal() > operand2.doubleVal()));
                    break;
                case STRING:
                    stack.push(new Operand(Type.BOOLEAN, operand1.stringVal().compareTo(operand2.stringVal()) > 0));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER, Type.REAL, Type.STRING);
                    break;
                }
                break;
            case LESS_THAN:
                switch (type) {
                case INTEGER:
                    stack.push(new Operand(Type.BOOLEAN, operand1.intVal() < operand2.intVal()));
                    break;
                case REAL:
                    stack.push(new Operand(Type.BOOLEAN, operand1.doubleVal() < operand2.doubleVal()));
                    break;
                case STRING:
                    stack.push(new Operand(Type.BOOLEAN, operand1.stringVal().compareTo(operand2.stringVal()) < 0));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER, Type.REAL, Type.STRING);
                    break;
                }
                break;
            case GREATER_THAN_OR_EQUAL:
                switch (type) {
                case INTEGER:
                    stack.push(new Operand(Type.BOOLEAN, operand1.intVal() >= operand2.intVal()));
                    break;
                case REAL:
                    stack.push(new Operand(Type.BOOLEAN, operand1.doubleVal() >= operand2.doubleVal()));
                    break;
                case STRING:
                    stack.push(new Operand(Type.BOOLEAN, operand1.stringVal().compareTo(operand2.stringVal()) >= 0));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER, Type.REAL, Type.STRING);
                    break;
                }
                break;
            case LESS_THAN_OR_EQUAL:
                switch (type) {
                case INTEGER:
                    stack.push(new Operand(Type.BOOLEAN, operand1.intVal() <= operand2.intVal()));
                    break;
                case REAL:
                    stack.push(new Operand(Type.BOOLEAN, operand1.doubleVal() <= operand2.doubleVal()));
                    break;
                case STRING:
                    stack.push(new Operand(Type.BOOLEAN, operand1.stringVal().compareTo(operand2.stringVal()) <= 0));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER, Type.REAL, Type.STRING);
                    break;
                }
                break;
            case APPROXIMATELY_EQUAL:
                if (type == Type.STRING) {
                    Pattern pattern;
                    String regex = operand2.stringVal();
                    if (!regexPatterns.containsKey(regex)) {
                        pattern = Pattern.compile(regex);
                        regexPatterns.put(regex, pattern);
                    }
                    pattern = regexPatterns.get(regex);
                    stack.push(new Operand(Type.BOOLEAN, pattern.matcher(operand1.stringVal()).matches()));
                } else {
                    binaryOpError(op, Type.STRING);
                    break;
                }
                break;
            case IN:
                if (type == Type.STRING) {
                    // Is one string contained within another?
                    stack.push(new Operand(Type.BOOLEAN, operand2.stringVal().contains(operand1.stringVal())));
                } else {
                    binaryOpError(op, Type.STRING);
                }
                break;
            case SHL:
                switch (type) {
                case INTEGER:
                    stack.push(new Operand(Type.INTEGER, operand1.intVal() << operand2.intVal()));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER);
                    break;
                }
                break;
            case SHR:
                switch (type) {
                case INTEGER:
                    stack.push(new Operand(Type.INTEGER, operand1.intVal() >> operand2.intVal()));
                    break;
                default:
                    binaryOpError(op, Type.INTEGER);
                    break;
                }
                break;
            default:
                break;
            }
        }
    }

    /**
     * Apply a binary operation to the operands, one of which is a list, leaving a
     * result on the stack.
     * 
     * @param op       The operation to be applied.
     * @param operand1 The first operand.
     * @param operand2 The second operand.
     */
    private void applyBinaryListOperation(Operation op, Operand operand1, Operand operand2) {
        switch (op) {
        case IN:
            Pair<Operand, Operand> operands = unifyTypes(operand1, operand2);
            operand1 = operands.first;
            operand2 = operands.second;

            if (operand2.getType() == Type.LIST) {
                // Is a value contained within a list?
                stack.push(new Operand(Type.BOOLEAN, operand2.listVal().contains(operand1)));
            } else if (operand2.getType() == Type.STRING) {
                // Is one string contained within another?
                stack.push(new Operand(Type.BOOLEAN, operand2.stringVal().contains(operand1.stringVal())));
            } else {
                String msg = String.format("The second operand must be of type list or string for 'IN' operation", op);
                throw new VeLaEvalError(msg);
            }
            break;

        default:
            List<Operand> result = new ArrayList<Operand>();

            if (operand1.getType() == Type.LIST && operand2.getType() == Type.LIST) {
                if (operand1.listVal().size() == operand2.listVal().size()) {
                    for (int i = 0; i < operand1.listVal().size(); i++) {
                        stack.push(operand1.listVal().get(i));
                        stack.push(operand2.listVal().get(i));
                        applyBinaryOperation(op);
                        result.add(stack.pop());
                    }
                    stack.push(new Operand(Type.LIST, result));
                } else {
                    String msg = String.format("Lists must be of equal length " + "for '%s' operation", op);
                    throw new VeLaEvalError(msg);
                }
            } else if (operand1.getType() != Type.LIST) {

                for (int i = 0; i < operand2.listVal().size(); i++) {
                    stack.push(operand1);
                    stack.push(operand2.listVal().get(i));
                    applyBinaryOperation(op);
                    result.add(stack.pop());
                }
                stack.push(new Operand(Type.LIST, result));
            } else {
                for (int i = 0; i < operand1.listVal().size(); i++) {
                    stack.push(operand1.listVal().get(i));
                    stack.push(operand2);
                    applyBinaryOperation(op);
                    result.add(stack.pop());
                }
                stack.push(new Operand(Type.LIST, result));
            }
            break;
        }
    }

    /**
     * Throw a VeLa evaluation error for the given operation and types.
     * 
     * @param op    The operator
     * @param types The expected types
     */
    private void binaryOpError(Operation op, Type... types) {
        String typeStr = "";
        for (int i = 0; i < types.length; i++) {
            typeStr += types[i].name();
            if (i < types.length - 1) {
                typeStr += " or ";
            }
        }
        String msg = String.format("'%s' expects values of type %s", op.token(), typeStr);
        throw new VeLaEvalError(msg);
    }

    /**
     * Unify operand types by converting both operands to strings if only one is a
     * string or both operands to double if only one is an integer. We change
     * nothing if either type is composite.
     * 
     * @param a The first operand.
     * @param b The second operand.
     * @return The unified operands.
     */
    private Pair<Operand, Operand> unifyTypes(Operand a, Operand b) {
        Operand converted_a = a;
        Operand converted_b = b;

        if (!a.getType().isComposite() && !b.getType().isComposite()) {
            if (a.getType() != Type.STRING && b.getType() == Type.STRING) {
                converted_a = a.convertToString();
            } else if (a.getType() == Type.STRING && b.getType() != Type.STRING) {
                converted_b = b.convertToString();
            } else if (a.getType() == Type.INTEGER && b.getType() == Type.REAL) {
                converted_a = new Operand(Type.REAL, (double) a.intVal());
            } else if (a.getType() == Type.REAL && b.getType() == Type.INTEGER) {
                converted_b = new Operand(Type.REAL, (double) b.intVal());
            }
        }

        return new Pair<Operand, Operand>(converted_a, converted_b);
    }

    // ** Variable related methods **

    /**
     * Given a variable name, search for it in the stack of environments, binding a
     * value if found and the type of the binding is compatible with the type of the
     * new value. The search proceeds from the top to the bottom of the stack,
     * maintaining the natural stack ordering. If the name is not found, a new
     * binding is created in the top-most scope.
     * 
     * @param name       The name to which to bind the value.
     * @param value      The value to be bound.
     * @param isConstant Is this a constant binding?
     */
    public void bind(String name, Operand value, boolean isConstant) {
        boolean bound = false;

        for (int i = environments.size() - 1; i >= 0; i--) {
            VeLaEnvironment<Operand> env = environments.get(i);
            if (env.isMutable()) {
                Optional<Operand> possibleBinding = env.lookup(name);
                if (possibleBinding.isPresent()) {
                    Operand existingBinding = possibleBinding.get();
                    Operand convertedVal = value.convert(existingBinding.getType());
                    if (convertedVal.getType() == existingBinding.getType()) {
                        // bind value to existing variable...
                        environments.get(i).bind(name, convertedVal, isConstant);
                        bound = true;
                    } else {
                        throw new VeLaEvalError(
                                String.format("The type of the value (%s) is not compatible with the bound type of %s.",
                                        value, name));
                    }
                    break;
                }
            }
        }

        if (!bound) {
            // If not bound to existing variable, create new binding in
            // environment on top of stack.
            VeLaEnvironment<Operand> env = environments.peek();
            if (env.isMutable()) {
                // ...a new binding
                environments.peek().bind(name, value, isConstant);
            } else {
                // The environment at stack-top should be mutable...
                throw new VeLaEvalError(String.format("The environment in which %s is bound is immutable.", name));
            }
        }
    }

    /**
     * Given a variable name, search for it in the stack of environments, return an
     * optional Operand instance. The search proceeds from the top to the bottom of
     * the stack, maintaining the natural stack ordering.
     * 
     * @param name The name of the variable to look up.
     * @return The optional operand.
     */
    public Optional<Operand> lookupBinding(String name) {
        Optional<Operand> result = Optional.empty();

        // Note: could use recursion or a reversed stream iterator instead

        for (int i = environments.size() - 1; i >= 0; i--) {
            result = environments.get(i).lookup(name);
            if (result.isPresent()) {
                break;
            }
        }

        return result;
    }

    /**
     * Read and interpret user-defined code.<br/>
     * A VeLa error should not bring VStar down.<br/>
     * Ignore all but VeLa files (e.g. could be README files) and directories.
     */
    private void loadUserCode() {
        for (File dir : sourceDirectories) {
            try {
                if (dir.isDirectory()) {
                    for (File file : dir.listFiles()) {
                        if (file.getName().endsWith(".vl") || file.getName().endsWith(".vela")) {
                            program(file);
                        }
                    }
                } else {
                }
            } catch (Throwable t) {
                VStar.LOGGER.warning("Error when sourcing VeLa code: " + t.getLocalizedMessage());
            }
        }
    }

    // ** Function related methods *

    /**
     * Given a function name, search for it in the stack of environments, return an
     * optional list of function executors. The search proceeds from the top to the
     * bottom of the stack, maintaining the natural stack ordering.
     * 
     * @param name The name of the variable to look up.
     * @return The optional function executor list.
     */
    public Optional<List<FunctionExecutor>> lookupFunctions(String name) {
        Optional<List<FunctionExecutor>> functions = Optional.empty();

        for (int i = environments.size() - 1; i >= 0; i--) {
            VeLaEnvironment<Operand> environment = environments.get(i);
            if (environment instanceof VeLaScope) {
                functions = ((VeLaScope) environment).lookupFunction(name);
                if (functions.isPresent()) {
                    break;
                }
            }
        }

        return functions;
    }

    /**
     * Apply the function to the supplied parameter list, leaving the result on the
     * stack.
     * 
     * @param funcName The name of the function.
     * @param params   The parameter list.
     * @throws VeLaEvalError If a function evaluation error occurs.
     */
    private void applyFunction(String funcName, List<Operand> params) throws VeLaEvalError {

        String canonicalFuncName = funcName.toUpperCase();

        // Iterate over all variations of each potentially overloaded function,
        // asking whether each conforms.

        Optional<List<FunctionExecutor>> functions = lookupFunctions(canonicalFuncName);

        boolean match = false;

        if (functions.isPresent()) {
            // First look for the name in the function namespace and try to
            // apply it.
            for (FunctionExecutor function : functions.get()) {
                match = applyFunction(function, params);
                if (match) {
                    break;
                }
            }

            if (!match) {
                StringBuffer candidateFunStr = new StringBuffer();
                for (FunctionExecutor candidateFun : functions.get()) {
                    candidateFunStr.append(" ");
                    candidateFunStr.append(candidateFun.toString());
                    candidateFunStr.append("\n");
                }
                throw new VeLaEvalError("Invalid parameters for function \"" + funcName + "\":\n" + candidateFunStr);
            }
        } else {
            // Instead of being a named function, it may be a function that's
            // been bound to a symbol, so try that next.
            Optional<Operand> value = lookupBinding(canonicalFuncName);

            if (value.isPresent()) {
                if (value.get().getType() == Type.FUNCTION) {
                    applyFunction(value.get().functionVal(), params);
                }
            } else {
                throw new VeLaEvalError("Unknown function \"" + funcName + "\"");
            }
        }
    }

    /**
     * Apply the function to the supplied parameter list if it conforms to them,
     * leaving the result on the stack.
     * 
     * @param function The function executor to be applied to the supplied
     *                 parameters.
     * @param params   The actual parameter list.
     * @return Does the function conform to the actual parameters?
     * @throws VeLaEvalError If a function evaluation error occurs.
     */
    private boolean applyFunction(FunctionExecutor function, List<Operand> params) throws VeLaEvalError {

        boolean conforms = function.conforms(params);

        if (conforms) {
            // Apply the function to the actual parameters.
            Optional<Operand> result = function.apply(params);

            String funcRepr = function.toString();

            if (result.isPresent()) {
                // The function returned a result.
                // Does the function have a return type defined?
                if (function.returnType.isPresent()) {
                    // Attempt to convert to return type if necessary.

                    Operand convertedResult = result.get().convert(function.getReturnType().get());
                    if (convertedResult.getType() == function.returnType.get()) {
                        // The returned result was of the expected type or was converted to it.
                        stack.push(convertedResult);
                    } else {
                        // The returned result was not of the expected type.
                        throw new VeLaEvalError(String.format(
                                "The expected return type of %s does not match " + "the actual return type of %s.",
                                funcRepr, result.get().getType()));
                    }
                } else {
                    throw new VeLaEvalError(
                            String.format("%s has no return type but a value " + "of type %s was returned.", funcRepr,
                                    result.get().getType()));
                }
            } else {
                if (function.returnType.isPresent()) {
                    // No result was returned but one was expected.
                    throw new VeLaEvalError(String.format("No value was returned by %s.", funcRepr));
                }
            }
        }

        return conforms;
    }

    /**
     * Add a function executor to the current scope.
     * 
     * @param executor The function executor to be added.
     */
    public void addFunctionExecutor(FunctionExecutor executor) {
        // It's possible that the top-most environment is not a scope, so find
        // the top-most scope and add the function executor to it.
        for (int i = environments.size() - 1; i >= 0; i--) {
            VeLaEnvironment<Operand> environment = environments.get(i);
            if (environment instanceof VeLaScope) {
                VeLaScope scope = (VeLaScope) environment;
                scope.addFunctionExecutor(executor);
            }
        }
    }

    /**
     * Add useful/important bindings
     */
    private void initBindings() {
        bind("Π", new Operand(Type.REAL, Math.PI), true);
        bind("PI", new Operand(Type.REAL, Math.PI), true);
        bind("E", new Operand(Type.REAL, Math.E), true);
    }

    /**
     * Initialise function executors
     */
    private void initFunctionExecutors() {

        // Special functions
        addEval();
        addExit();
        addHelp();
        addZeroArityFunctions();

        // I/O
        addIOProcedures();

        // String functions
        addFormatFunction();
        addChrFunction();
        addOrdFunction();

        // List functions
        addListHeadFunction();
        addListTailFunction();
        addListNthFunction();
        addListLengthFunction();
        addListConcatFunction();
        addIntegerSeqFunction();
        addRealSeqFunction();
        addListMapFunction();
        addListFilterFunction();
        addListFindFunction();
        addListPairwiseFindFunction();
        addListForFunction();

        for (Type type : Type.values()) {
            if (!type.equals(Type.OBJECT) && !type.equals(Type.NONE)) {
                addListAppendFunction(type);
                addListReduceFunction(type);
            }
        }

        // Collect functions from reflection over Java classes
        for (FunctionExecutor function : javaClassFunctionExecutors) {
            addFunctionExecutor(function);
            if (verbose && function != null) {
                System.out.println(function.toString());
            }
        }
    }

    private void addEval() {
        String help = "Compiles and evaluates a VeLa program given\n"
                + "in the supplied string, returning the empty list if\n"
                + "there is no result, or a single element list if\n" + "there is a result.";

        addFunctionExecutor(new FunctionExecutor(Optional.of("EVAL"), Arrays.asList("code"), Arrays.asList(Type.STRING),
                Optional.of(Type.LIST), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                // Compile and evaluate code.
                program(operands.get(0).stringVal());
                Optional<Operand> result = program(operands.get(0).stringVal());

                // Return a list containing the result or the empty list.
                Optional<Operand> resultList;
                if (result.isPresent()) {
                    resultList = Optional.of(new Operand(Type.LIST, Arrays.asList(result.get())));
                } else {
                    resultList = Optional.of(Operand.EMPTY_LIST);
                }

                return resultList;
            }
        });
    }

    private void addZeroArityFunctions() {
        String todayHelp = "Yields the Julian Day corresponding to the current year, month and day.";

        addFunctionExecutor(new FunctionExecutor(Optional.of("TODAY"), Optional.of(Type.REAL), Optional.of(todayHelp)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                Calendar cal = Calendar.getInstance();
                int year = cal.get(Calendar.YEAR);
                int month = cal.get(Calendar.MONTH) + 1; // 0..11 -> 1..12
                int day = cal.get(Calendar.DAY_OF_MONTH);
                double jd = AbstractDateUtil.getInstance().calendarToJD(year, month, day);
                return Optional.of(new Operand(Type.REAL, jd));
            }
        });

        String intrinsicsHelp = "Returns a list of intrinsic functions.";

        addFunctionExecutor(
                new FunctionExecutor(Optional.of("INTRINSICS"), Optional.of(Type.LIST), Optional.of(intrinsicsHelp)) {
                    @Override
                    public Optional<Operand> apply(List<Operand> operands) throws VeLaEvalError {
                        List<Operand> funcInfoList = new ArrayList<Operand>();
                        VeLaScope environment = (VeLaScope) environments.get(0);
                        Map<String, List<FunctionExecutor>> functionMap = new TreeMap<String, List<FunctionExecutor>>(
                                environment.getFunctions());
                        for (String name : functionMap.keySet()) {
                            for (FunctionExecutor function : functionMap.get(name)) {
                                if (!(function instanceof UserDefinedFunctionExecutor)) {
                                    Operand funcInfo = new Operand(Type.STRING, function.toString());
                                    funcInfoList.add(funcInfo);
                                }
                            }
                        }
                        return Optional.of(new Operand(Type.LIST, funcInfoList));
                    }
                });

        String millisecsHelp = "Returns the number of milliseconds between the current time and midnight, January 1, 1970 UTC";

        addFunctionExecutor(new FunctionExecutor(Optional.of("MILLISECONDS"), Optional.of(Type.INTEGER),
                Optional.of(millisecsHelp)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                long milliseconds = System.currentTimeMillis();
                return Optional.of(new Operand(Type.INTEGER, milliseconds));
            }
        });

    }

    private void addExit() {
        String help = "Exits the current program with the specified exit code.";

        addFunctionExecutor(new FunctionExecutor(Optional.of("EXIT"), Arrays.asList("exitCode"),
                Arrays.asList(Type.INTEGER), Optional.empty(), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                System.exit((int) operands.get(0).intVal());
                return Optional.empty();
            }
        });
    }

    private void addHelp() {
        String help = "Returns a help string given an arbitrary parameter.";

        addFunctionExecutor(new FunctionExecutor(Optional.of("HELP"), FunctionExecutor.ANY_FORMAL_NAMES,
                FunctionExecutor.ANY_FORMAL_TYPES, Optional.of(Type.STRING), Optional.of(help)) {

            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                String helpMessage = null;
                if (operands.size() >= 1) {
                    StringBuffer buf = new StringBuffer();
                    for (Operand operand : operands) {
                        String humanStr = operand.toHumanReadableString();

                        if (operand.getType() != Type.FUNCTION) {
                            buf.append(operand.getType());
                            buf.append(" : ");
                            buf.append(humanStr);
                            buf.append("\n");
                        } else {
                            buf.append(humanStr);
                            buf.append("\n");
                            Optional<String> helpStrOpt = operand.functionVal().helpString;
                            if (helpStrOpt.isPresent()) {
                                buf.append(helpStrOpt.get());
                                buf.append("\n");
                            }
                        }

                        buf.append("\n");
                    }
                    helpMessage = buf.toString();
                } else {
                    throw new VeLaEvalError("One or more expression expected.");
                }

                return Optional.of(new Operand(Type.STRING, helpMessage));
            }
        });
    }

    private void addIOProcedures() {
        // Any number or type of parameters will do.
        String printHelp = "Prints an arbitrary number of parameters (or none) to standard output.";

        addFunctionExecutor(new FunctionExecutor(Optional.of("PRINT"), FunctionExecutor.ANY_FORMAL_NAMES,
                FunctionExecutor.ANY_FORMAL_TYPES, Optional.empty(), Optional.of(printHelp)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                return commonPrintProcedure(operands, false);
            }
        });

        // Note: shouldn't need this but on command-line, the LF character
        // prints literally. Why?
        String printlnHelp = "Prints an arbitrary number of parameters (or none) to standard output, followed by a newline sequence.";

        addFunctionExecutor(new FunctionExecutor(Optional.of("PRINTLN"), FunctionExecutor.ANY_FORMAL_NAMES,
                FunctionExecutor.ANY_FORMAL_TYPES, Optional.empty(), Optional.of(printlnHelp)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                return commonPrintProcedure(operands, true);
            }
        });

        String nextchHelp = "Gets and returns the next character from standard input.";

        addFunctionExecutor(
                new FunctionExecutor(Optional.of("NEXTCHAR"), Optional.of(Type.STRING), Optional.of(nextchHelp)) {
                    @Override
                    public Optional<Operand> apply(List<Operand> operands) {
                        Operand ch = null;
                        try {
                            ch = new Operand(Type.STRING, Character.toString((char) System.in.read()));
                        } catch (IOException e) {
                            ch = new Operand(Type.STRING, "");
                        }
                        return Optional.of(ch);
                    }
                });
    }

    private Optional<Operand> commonPrintProcedure(List<Operand> operands, boolean eoln) {
        for (Operand operand : operands) {
            System.out.print(operand.toHumanReadableString());
        }
        if (eoln) {
            System.out.println();
        }
        return Optional.empty();
    }

    private void addFormatFunction() {
        String help = "Given a format string and a list of expressions, yields a formatted string.";

        addFunctionExecutor(new FunctionExecutor(Optional.of("FORMAT"), Arrays.asList("formatString", "valueList"),
                Arrays.asList(Type.STRING, Type.LIST), Optional.of(Type.STRING), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                List<Object> args = new ArrayList<Object>();
                for (Operand operand : operands.get(1).listVal()) {
                    switch (operand.getType()) {
                    case INTEGER:
                        args.add(operand.intVal());
                        break;
                    case REAL:
                        args.add(operand.doubleVal());
                        break;
                    case STRING:
                        args.add(operand.stringVal());
                        break;
                    case BOOLEAN:
                        args.add(operand.booleanVal());
                        break;
                    case LIST:
                        args.add(operand.listVal());
                        break;
                    case FUNCTION:
                        args.add(operand.functionVal());
                        break;
                    }
                }
                Operand result = new Operand(Type.STRING,
                        String.format(operands.get(0).stringVal(), args.toArray(new Object[0])));
                return Optional.of(result);
            }
        });
    }

    private void addChrFunction() {
        String help = "Returns a single character string given an ordinal value.";

        addFunctionExecutor(new FunctionExecutor(Optional.of("CHR"), Arrays.asList("ordinalValue"),
                Arrays.asList(Type.INTEGER), Optional.of(Type.STRING), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                long ordVal = operands.get(0).intVal();
                String str = ordVal > -1 ? Character.toString((char) ordVal) : "";
                return Optional.of(new Operand(Type.STRING, str));
            }
        });
    }

    private void addOrdFunction() {
        String help = "Returns an ordinal value given a single character string.";

        addFunctionExecutor(new FunctionExecutor(Optional.of("ORD"), Arrays.asList("character"),
                Arrays.asList(Type.STRING), Optional.of(Type.INTEGER), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                char chrVal = operands.get(0).stringVal().charAt(0);
                return Optional.of(new Operand(Type.INTEGER, (int) chrVal));
            }
        });
    }

    private void addListHeadFunction() {
        String help = "Returns the head of a list or the empty list if the list is empty.";

        // Return type will change with invocation.
        addFunctionExecutor(new FunctionExecutor(Optional.of("HEAD"), Arrays.asList("aList"), Arrays.asList(Type.LIST),
                Optional.of(Type.LIST), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                List<Operand> list = operands.get(0).listVal();
                Operand result;
                if (!list.isEmpty()) {
                    result = list.get(0);
                } else {
                    result = Operand.EMPTY_LIST;
                }
                setReturnType(Optional.of(result.getType()));
                return Optional.of(result);
            }
        });
    }

    private void addListTailFunction() {
        String help = "Returns the tail of a list or the empty list if the list is empty.";

        // Return type will always be a list.
        addFunctionExecutor(new FunctionExecutor(Optional.of("TAIL"), Arrays.asList("aList"), Arrays.asList(Type.LIST),
                Optional.of(Type.LIST), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                List<Operand> list = operands.get(0).listVal();
                Operand result;
                if (!list.isEmpty()) {
                    List<Operand> tail = new ArrayList<Operand>(list);
                    tail.remove(0);
                    result = new Operand(Type.LIST, tail);
                } else {
                    result = Operand.EMPTY_LIST;
                }
                return Optional.of(result);
            }
        });
    }

    private void addListNthFunction() {
        String help = "Returns the nth element of a list or the empty list if the list is empty.";

        // Return type will change with invocation; need a union type!
        addFunctionExecutor(new FunctionExecutor(Optional.of("NTH"), Arrays.asList("aList", "index"),
                Arrays.asList(Type.LIST, Type.INTEGER), Optional.empty(), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                List<Operand> list = operands.get(0).listVal();
                Operand result;
                if (!list.isEmpty()) {
                    result = list.get((int) operands.get(1).intVal());
                } else {
                    result = Operand.EMPTY_LIST;
                }
                setReturnType(Optional.of(result.getType()));
                return Optional.of(result);
            }
        });
    }

    private void addListLengthFunction() {
        String help = "Returns the length of a list.";

        // Return type will always be integer.
        addFunctionExecutor(new FunctionExecutor(Optional.of("LENGTH"), Arrays.asList("aList"),
                Arrays.asList(Type.LIST), Optional.of(Type.INTEGER), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                return Optional.of(new Operand(Type.INTEGER, operands.get(0).listVal().size()));
            }
        });
    }

    private void addListConcatFunction() {
        String help = "Returns the concatenation of two lists.";

        // Return type will always be LIST here.
        addFunctionExecutor(new FunctionExecutor(Optional.of("CONCAT"), Arrays.asList("aList", "anotherList"),
                Arrays.asList(Type.LIST, Type.LIST), Optional.of(Type.LIST), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                List<Operand> list1 = operands.get(0).listVal();
                List<Operand> list2 = operands.get(1).listVal();
                List<Operand> newList = new ArrayList<Operand>();
                newList.addAll(list1);
                newList.addAll(list2);
                return Optional.of(new Operand(Type.LIST, newList));
            }
        });
    }

    private void addListAppendFunction(Type secondParameterType) {
        String help = "Returns the result of appending an expression to a list.";

        // Return type will always be LIST here.
        addFunctionExecutor(new FunctionExecutor(Optional.of("APPEND"), Arrays.asList("aList", "newElement"),
                Arrays.asList(Type.LIST, secondParameterType), Optional.of(Type.LIST), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                List<Operand> newList = new ArrayList<Operand>();
                newList.addAll(operands.get(0).listVal());
                newList.add(operands.get(1));
                return Optional.of(new Operand(Type.LIST, newList));
            }
        });
    }

    private void addIntegerSeqFunction() {
        String help = "Returns a list which is the sequence of the (inclusive) range\n"
                + "specified by the first and second parameters,\n"
                + "combined with the step, specified by the third parameter.";

        // Return type will always be LIST here..
        addFunctionExecutor(new FunctionExecutor(Optional.of("SEQ"), Arrays.asList("first", "last", "step"),
                Arrays.asList(Type.INTEGER, Type.INTEGER, Type.INTEGER), Optional.of(Type.LIST), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                Long first = operands.get(0).intVal();
                Long last = operands.get(1).intVal();
                Long step = operands.get(2).intVal();
                List<Operand> resultList = new ArrayList<Operand>();
                for (long i = first; i <= last; i += step) {
                    resultList.add(new Operand(Type.INTEGER, i));
                }
                return Optional.of(new Operand(Type.LIST, resultList));
            }
        });
    }

    private void addRealSeqFunction() {
        String help = "Returns a list which is the sequence of the range\n"
                + "specified by the first and second parameters,\n"
                + "combined with the step, specified by the third parameter.";

        // Return type will always be LIST here..
        addFunctionExecutor(new FunctionExecutor(Optional.of("SEQ"), Arrays.asList("firstIndex", "lastIndex", "step"),
                Arrays.asList(Type.REAL, Type.REAL, Type.REAL), Optional.of(Type.LIST), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                Double first = operands.get(0).doubleVal();
                Double last = operands.get(1).doubleVal();
                Double step = operands.get(2).doubleVal();
                List<Operand> resultList = new ArrayList<Operand>();
                for (double i = first; i <= last; i += step) {
                    resultList.add(new Operand(Type.REAL, i));
                }
                return Optional.of(new Operand(Type.LIST, resultList));
            }
        });
    }

    private void addListMapFunction() {
        String help = "Applies a function to each element of a list and returns a corresponding list.";

        // Return type will always be LIST here.
        addFunctionExecutor(new FunctionExecutor(Optional.of("MAP"), Arrays.asList("unaryFunction", "aList"),
                Arrays.asList(Type.FUNCTION, Type.LIST), Optional.of(Type.LIST), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                FunctionExecutor fun = operands.get(0).functionVal();
                List<Operand> list = operands.get(1).listVal();
                List<Operand> resultList = new ArrayList<Operand>();
                for (Operand item : list) {
                    List<Operand> params = Arrays.asList(item);
                    applyFunction(fun, params);

                    if (!stack.isEmpty()) {
                        resultList.add(stack.pop());
                    } else {
                        throw new VeLaEvalError("Expected function result");
                    }
                }
                return Optional.of(new Operand(Type.LIST, resultList));
            }
        });
    }

    private void addListFilterFunction() {
        String help = "Applies a function (predicate) to each element of a list and returns\n"
                + "the subset of those elements that satisfy the predicate.";

        // Return type will always be LIST here.
        addFunctionExecutor(new FunctionExecutor(Optional.of("FILTER"), Arrays.asList("predicate", "aList"),
                Arrays.asList(Type.FUNCTION, Type.LIST), Optional.of(Type.LIST), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                FunctionExecutor fun = operands.get(0).functionVal();
                List<Operand> list = operands.get(1).listVal();
                List<Operand> resultList = new ArrayList<Operand>();
                for (Operand item : list) {
                    List<Operand> params = Arrays.asList(item);
                    applyFunction(fun, params);

                    if (!stack.isEmpty()) {
                        Operand retVal = stack.pop();
                        if (retVal.getType() == Type.BOOLEAN) {
                            if (retVal.booleanVal()) {
                                resultList.add(item);
                            }
                        } else {
                            throw new VeLaEvalError("Expected boolean value");
                        }
                    } else {
                        throw new VeLaEvalError("Expected boolean value");
                    }
                }
                return Optional.of(new Operand(Type.LIST, resultList));
            }
        });
    }

    private void addListFindFunction() {
        // Return the index of the first element of a list matching a
        // predicate, else -1.

        String findHelp = "Return the index of the first element of a list matching a\n"
                + "predicate applied to a list element, else -1";

        addFunctionExecutor(new FunctionExecutor(Optional.of("FIND"), Arrays.asList("unaryFunction", "aList"),
                Arrays.asList(Type.FUNCTION, Type.LIST), Optional.of(Type.INTEGER), Optional.of(findHelp)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                FunctionExecutor fun = operands.get(0).functionVal();
                List<Operand> list = operands.get(1).listVal();
                int index = -1;
                for (int i = 0; i < list.size(); i++) {
                    List<Operand> params = Arrays.asList(list.get(i));
                    applyFunction(fun, params);

                    if (!stack.isEmpty()) {
                        Operand retVal = stack.pop();
                        if (retVal.getType() == Type.BOOLEAN) {
                            if (retVal.booleanVal()) {
                                index = i;
                                break;
                            }
                        } else {
                            throw new VeLaEvalError("Expected boolean value");
                        }
                    } else {
                        throw new VeLaEvalError("Expected boolean value");
                    }
                }
                return Optional.of(new Operand(Type.INTEGER, index));
            }
        });
    }

    private void addListPairwiseFindFunction() {
        // Return the index of the first element of a list matching a
        // predicate applied to two list elements, else -1. Whereas FIND's predicate
        // takes a single list element,
        // PAIRWISEFIND takes two elements separated by a "step" value.

        String pairwiseFindHelp = "Return the index of the first element of a list matching a\n"
                + "predicate applied to two list elements, else -1";

        addFunctionExecutor(new FunctionExecutor(Optional.of("PAIRWISEFIND"),
                Arrays.asList("unaryFunction", "aList", "step"), Arrays.asList(Type.FUNCTION, Type.LIST, Type.INTEGER),
                Optional.of(Type.INTEGER), Optional.of(pairwiseFindHelp)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                FunctionExecutor fun = operands.get(0).functionVal();
                List<Operand> list = operands.get(1).listVal();
                long step = (int) operands.get(2).intVal();
                int index = -1;
                for (int i = 0; i < list.size() - 1; i += step) {
                    List<Operand> params = Arrays.asList(list.get(i), list.get(i + 1));
                    applyFunction(fun, params);

                    if (!stack.isEmpty()) {
                        Operand retVal = stack.pop();
                        if (retVal.getType() == Type.BOOLEAN) {
                            if (retVal.booleanVal()) {
                                index = i;
                                break;
                            }
                        } else {
                            throw new VeLaEvalError("Expected boolean value");
                        }
                    } else {
                        throw new VeLaEvalError("Expected boolean value");
                    }
                }
                return Optional.of(new Operand(Type.INTEGER, index));
            }
        });
    }

    private void addListReduceFunction(Type reductionType) {
        String help = "Applies a function to each element of a list, returning\n"
                + "a single value. An initial value must be provided.";

        // Return type will be same as function the parameter's type.
        addFunctionExecutor(new FunctionExecutor(Optional.of("REDUCE"),
                Arrays.asList("unaryFunction", "aList", "initialValue"),
                Arrays.asList(Type.FUNCTION, Type.LIST, reductionType), Optional.of(reductionType), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                FunctionExecutor fun = operands.get(0).functionVal();
                // Set return type on reduce dynamically each time.
                setReturnType(fun.getReturnType());
                List<Operand> list = operands.get(1).listVal();
                Operand retVal = operands.get(2); // base value
                for (Operand item : list) {
                    List<Operand> params = Arrays.asList(retVal, item);
                    applyFunction(fun, params);

                    if (!stack.isEmpty()) {
                        retVal = stack.pop();
                    }
                }
                return Optional.of(retVal);
            }
        });
    }

    private void addListForFunction() {
        String help = "Invokes a function on each element of a list.";

        // FOR should not return anything.
        addFunctionExecutor(new FunctionExecutor(Optional.of("FOR"), Arrays.asList("unaryFunction", "aList"),
                Arrays.asList(Type.FUNCTION, Type.LIST), Optional.empty(), Optional.of(help)) {
            @Override
            public Optional<Operand> apply(List<Operand> operands) {
                FunctionExecutor fun = operands.get(0).functionVal();
                List<Operand> list = operands.get(1).listVal();
                for (Operand item : list) {
                    List<Operand> params = new ArrayList<Operand>();
                    params.add(item);

                    applyFunction(fun, params);
                }
                return Optional.empty();
            }
        });
    }

    /**
     * Given a class, add non zero-arity VeLa type-compatible functions to the
     * functions map.
     * 
     * @param clazz          The class from which to add function executors.
     * @param instance       The instance of this class on which to invoke the
     *                       function.
     * @param permittedTypes The set of Java types that are compatible with VeLa.
     * @param exclusions     Names of functions to exclude.
     */
    public void addFunctionExecutorsFromClass(Class<?> clazz, Object instance, Set<Class<?>> permittedTypes,
            Set<String> exclusions) {
        Method[] declaredMethods = clazz.getDeclaredMethods();

        for (Method declaredMethod : declaredMethods) {
            String funcName = declaredMethod.getName().toUpperCase();
            List<Class<?>> paramTypes = getJavaParameterTypes(declaredMethod, permittedTypes);
            Class<?> returnType = declaredMethod.getReturnType();

            // If the method is non-static, we need to include a parameter
            // type for the object on which the method will be invoked.
            if (!Modifier.isStatic(declaredMethod.getModifiers()) && instance == null) {
                List<Class<?>> newParamTypes = new ArrayList<Class<?>>();
                newParamTypes.add(clazz);
                newParamTypes.addAll(paramTypes);
                paramTypes = newParamTypes;
            }

            FunctionExecutor function = null;

            if (!exclusions.contains(funcName) && permittedTypes.contains(returnType)) {
                List<String> names = getJavaParameterNames(declaredMethod, permittedTypes);
                List<Type> types = paramTypes.stream().map(t -> Type.java2Vela(t)).collect(Collectors.toList());

                Optional<String> helpString = Optional.empty();

                function = new JavaMethodExecutor(instance, declaredMethod, Optional.of(funcName), names, types,
                        Optional.of(Type.java2Vela(returnType)), helpString);

                javaClassFunctionExecutors.add(function);
            }
        }
    }

    // TODO
    // unify these two methods, e.g. via a Parameter class

    private static List<String> getJavaParameterNames(Method method, Set<Class<?>> targetTypes) {
        Parameter[] parameters = method.getParameters();
        List<String> parameterNames = new ArrayList<String>();

        for (Parameter parameter : parameters) {
            String name = parameter.getName();
            if (targetTypes.contains(parameter.getType())) {
                parameterNames.add(name);
            }
        }

        return parameterNames;
    }

    private static List<Class<?>> getJavaParameterTypes(Method method, Set<Class<?>> targetTypes) {
        Parameter[] parameters = method.getParameters();
        List<Class<?>> parameterTypes = new ArrayList<Class<?>>();

        for (Parameter parameter : parameters) {
            Class<?> type = parameter.getType();
            if (targetTypes.contains(type)) {
                parameterTypes.add(type);
            }
        }

        return parameterTypes;
    }
}

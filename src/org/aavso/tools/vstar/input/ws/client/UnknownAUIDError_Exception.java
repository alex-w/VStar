
package org.aavso.tools.vstar.input.ws.client;

import javax.xml.ws.WebFault;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.1.6 in JDK 6
 * Generated source version: 2.1
 * 
 */
@WebFault(name = "UnknownAUIDError", targetNamespace = "http://endpoint.ws.input.vstar.tools.aavso.org/")
public class UnknownAUIDError_Exception
    extends Exception
{

    /**
     * Java type that goes as soapenv:Fault detail element.
     * 
     */
    private UnknownAUIDError faultInfo;

    /**
     * 
     * @param message
     * @param faultInfo
     */
    public UnknownAUIDError_Exception(String message, UnknownAUIDError faultInfo) {
        super(message);
        this.faultInfo = faultInfo;
    }

    /**
     * 
     * @param message
     * @param faultInfo
     * @param cause
     */
    public UnknownAUIDError_Exception(String message, UnknownAUIDError faultInfo, Throwable cause) {
        super(message, cause);
        this.faultInfo = faultInfo;
    }

    /**
     * 
     * @return
     *     returns fault bean: org.aavso.tools.vstar.input.ws.client.UnknownAUIDError
     */
    public UnknownAUIDError getFaultInfo() {
        return faultInfo;
    }

}
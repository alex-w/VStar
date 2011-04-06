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
package org.aavso.tools.vstar.util;

/**
 * T Cas test data as supplied with the AAVSO's ts1201.f Time Series Fortran
 * code from AAVSO, by Matthew Templeton and Grant Foster.
 */
public class TCasData {

	public static final double[][] data = { { 47003.5684, 11.7000 },
			{ 47010.5407, 11.6875 }, { 47021.8561, 11.7083 },
			{ 47029.2709, 11.7400 }, { 47038.4757, 11.7571 },
			{ 47052.2739, 11.5083 }, { 47060.5963, 11.4950 },
			{ 47069.4640, 11.0000 }, { 47081.0427, 10.2647 },
			{ 47089.6299, 9.5593 }, { 47098.0038, 9.2917 },
			{ 47112.0430, 8.9636 }, { 47120.9607, 8.8765 },
			{ 47131.0238, 8.8818 }, { 47139.5317, 8.8000 },
			{ 47150.6185, 8.9261 }, { 47159.6000, 8.7118 },
			{ 47170.0499, 8.9038 }, { 47180.7742, 8.8368 },
			{ 47190.0126, 8.6500 }, { 47200.4612, 8.7667 },
			{ 47209.4863, 8.8711 }, { 47217.6407, 8.8625 },
			{ 47227.8922, 8.5529 }, { 47239.9536, 8.3429 },
			{ 47248.5667, 8.3000 }, { 47262.9154, 8.2154 },
			{ 47271.3867, 8.1400 }, { 47280.6429, 8.1429 },
			{ 47290.9088, 8.1875 }, { 47300.4666, 8.4636 },
			{ 47309.6426, 8.5750 }, { 47321.4500, 8.9750 },
			{ 47329.6271, 9.1364 }, { 47341.4963, 9.4071 },
			{ 47351.5836, 9.8941 }, { 47360.4860, 10.3176 },
			{ 47369.8632, 10.7333 }, { 47380.7299, 11.1842 },
			{ 47389.4961, 11.3375 }, { 47399.1541, 11.5267 },
			{ 47411.3265, 11.7370 }, { 47420.0914, 11.8045 },
			{ 47430.0593, 11.9000 }, { 47439.2429, 11.9478 },
			{ 47450.2257, 11.9588 }, { 47460.7404, 11.9333 },
			{ 47469.2076, 11.8571 }, { 47480.1656, 11.8333 },
			{ 47491.0182, 11.3727 }, { 47500.9629, 11.0320 },
			{ 47509.3253, 10.6462 }, { 47519.8413, 10.0538 },
			{ 47528.8256, 9.6409 }, { 47539.0342, 9.3889 },
			{ 47551.6140, 9.3071 }, { 47560.6750, 9.3412 },
			{ 47568.9280, 9.4400 }, { 47580.0072, 9.3938 },
			{ 47590.7887, 9.5929 }, { 47599.2001, 9.3833 },
			{ 47609.6262, 9.4462 }, { 47617.7858, 9.6200 },
			{ 47630.0020, 9.2600 }, { 47638.4500, 9.2750 },
			{ 47650.5870, 8.8700 }, { 47662.4667, 8.8667 },
			{ 47671.2478, 8.6500 }, { 47680.0444, 8.4333 },
			{ 47691.0200, 8.0600 }, { 47698.7596, 8.0556 },
			{ 47710.5607, 8.1357 }, { 47719.4446, 8.2750 },
			{ 47730.0078, 8.4071 }, { 47741.2246, 8.5722 },
			{ 47749.9070, 8.9200 }, { 47761.6323, 9.3562 },
			{ 47770.2394, 9.6905 }, { 47779.6892, 10.0905 },
			{ 47791.7764, 10.3455 }, { 47801.1417, 10.7032 },
			{ 47808.5663, 10.9600 }, { 47820.9109, 11.2556 },
			{ 47830.2777, 11.4538 }, { 47840.5543, 11.6385 },
			{ 47851.0311, 11.8739 }, { 47859.3312, 11.8520 },
			{ 47869.3116, 11.9125 }, { 47880.6122, 12.0462 },
			{ 47890.7688, 12.0313 }, { 47899.2570, 12.0455 },
			{ 47910.5288, 11.9684 }, { 47919.6943, 11.8000 },
			{ 47930.7554, 11.6067 }, { 47941.0819, 11.3846 },
			{ 47950.0332, 11.3846 }, { 47960.2150, 10.9250 },
			{ 47968.1233, 10.7867 }, { 47980.0083, 10.5583 },
			{ 47992.0760, 10.5200 }, { 47998.7288, 10.5000 },
			{ 48010.8496, 10.2000 }, { 48018.8000, 10.1000 },
			{ 48031.5667, 10.0667 }, { 48040.0022, 9.6778 },
			{ 48061.0996, 8.8875 }, { 48069.8257, 8.4571 },
			{ 48079.0601, 8.5000 }, { 48089.6213, 8.3913 },
			{ 48099.4871, 8.3344 }, { 48109.6716, 8.3105 },
			{ 48119.8662, 8.3471 }, { 48129.7542, 8.1600 },
			{ 48140.3591, 8.0536 }, { 48149.6238, 8.1861 },
			{ 48160.0388, 8.3575 }, { 48170.8118, 8.7273 },
			{ 48178.8247, 8.9341 }, { 48189.3672, 9.1824 },
			{ 48201.9370, 9.3722 }, { 48209.3259, 9.6893 },
			{ 48219.1467, 9.8300 }, { 48231.6846, 10.2680 },
			{ 48239.1638, 10.0000 }, { 48248.5486, 10.6091 },
			{ 48260.7268, 10.9308 }, { 48271.0923, 11.0722 },
			{ 48279.4824, 10.9385 }, { 48290.7073, 11.2375 },
			{ 48299.5259, 11.1769 }, { 48308.2283, 11.2800 },
			{ 48321.7009, 11.4444 }, { 48329.6880, 11.5833 },
			{ 48343.9099, 11.7000 }, { 48351.2241, 11.6600 },
			{ 48359.3899, 11.5333 }, { 48380.3252, 11.5000 },
			{ 48390.9629, 11.3333 }, { 48396.1001, 11.3500 },
			{ 48412.1309, 11.2000 }, { 48420.4121, 10.0700 },
			{ 48430.2634, 9.5167 }, { 48442.1897, 9.0000 },
			{ 48451.7485, 8.8231 }, { 48461.9749, 8.4500 },
			{ 48470.0071, 8.2556 }, { 48480.1179, 8.2667 },
			{ 48489.9624, 8.3762 }, { 48501.2285, 8.2281 },
			{ 48509.6023, 8.3778 }, { 48519.9041, 8.2800 },
			{ 48530.9473, 8.2886 }, { 48540.3118, 8.3471 },
			{ 48548.2856, 8.3762 }, { 48559.7793, 8.4440 },
			{ 48569.5684, 8.2839 }, { 48579.9109, 8.2318 },
			{ 48589.7559, 8.3174 }, { 48600.6345, 8.4152 },
			{ 48611.2344, 8.5762 }, { 48620.5674, 8.6000 },
			{ 48630.3418, 8.8345 }, { 48640.8767, 8.8667 },
			{ 48651.3557, 9.0714 }, { 48659.4836, 9.1588 },
			{ 48671.5098, 9.2357 }, { 48679.4741, 9.3048 },
			{ 48689.3201, 9.5500 }, { 48698.7534, 9.8000 },
			{ 48710.9131, 10.6500 }, { 48719.8894, 10.9500 },
			{ 48729.8650, 10.8667 }, { 48740.6499, 11.1500 },
			{ 48751.2871, 11.2500 }, { 48761.3633, 11.3667 },
			{ 48769.2251, 11.6250 }, { 48777.6499, 11.7000 },
			{ 48786.1499, 11.8500 }, { 48799.9268, 11.7571 },
			{ 48810.0239, 11.5444 }, { 48821.9248, 11.4500 },
			{ 48830.5422, 11.2500 }, { 48840.4607, 11.0545 },
			{ 48851.8171, 10.6714 }, { 48860.8425, 10.3333 },
			{ 48869.9634, 10.0115 }, { 48880.3892, 9.5320 },
			{ 48889.4309, 9.4692 }, { 48899.2102, 9.1214 },
			{ 48910.1455, 8.7950 }, { 48920.1541, 8.6115 },
			{ 48929.3918, 8.4636 }, { 48938.6980, 8.4000 },
			{ 48949.9512, 8.3190 }, { 48959.6143, 8.1533 },
			{ 48971.0945, 8.2118 }, { 48980.1621, 8.1737 },
			{ 48988.7207, 8.0923 }, { 49000.6912, 8.0478 },
			{ 49009.8779, 8.0050 }, { 49020.0649, 8.0071 },
			{ 49030.4153, 8.1062 }, { 49040.4304, 8.1538 },
			{ 49048.3894, 8.2625 }, { 49059.6382, 8.4438 },
			{ 49069.5571, 8.6619 }, { 49079.4900, 9.2000 },
			{ 49089.4617, 9.3400 }, { 49101.0200, 9.6000 },
			{ 49114.8000, 10.3000 }, { 49120.3049, 9.8750 },
			{ 49130.0139, 10.1200 }, { 49141.6394, 10.5667 },
			{ 49152.4800, 10.7000 }, { 49160.5017, 10.8300 },
			{ 49171.0425, 11.1000 }, { 49179.0273, 11.1143 },
			{ 49189.3035, 11.3000 }, { 49200.2139, 11.5077 },
			{ 49209.5208, 11.6538 }, { 49220.6260, 11.6864 },
			{ 49228.6448, 11.7429 }, { 49239.5488, 11.7300 },
			{ 49250.3057, 11.7273 }, { 49259.2917, 11.6273 },
			{ 49269.4036, 11.6143 }, { 49280.8909, 11.3652 },
			{ 49290.4624, 11.0000 }, { 49300.4575, 10.6222 },
			{ 49309.4607, 10.5071 }, { 49320.3677, 9.8667 },
			{ 49330.5713, 9.4625 }, { 49339.1772, 9.2182 },
			{ 49350.1953, 8.9643 }, { 49359.1204, 9.0708 },
			{ 49370.1724, 8.7750 }, { 49380.3220, 8.7214 },
			{ 49389.7864, 8.4731 }, { 49398.6331, 8.4250 },
			{ 49410.9480, 8.2429 }, { 49418.8733, 8.1333 },
			{ 49429.6616, 8.2125 }, { 49441.4116, 8.2182 },
			{ 49449.3374, 8.2133 }, { 49460.3923, 8.0143 },
			{ 49473.4209, 8.2500 }, { 49479.7405, 8.3857 },
			{ 49487.3953, 8.8333 }, { 49501.7156, 9.1429 },
			{ 49509.7275, 8.9889 }, { 49519.4951, 9.3111 },
			{ 49530.4292, 9.6182 }, { 49540.6863, 10.0800 },
			{ 49549.8003, 10.1833 }, { 49559.6528, 10.4111 },
			{ 49570.2502, 10.4706 }, { 49580.3301, 10.6733 },
			{ 49592.3550, 10.9615 }, { 49600.0525, 10.8529 },
			{ 49608.7734, 10.8556 }, { 49620.6082, 11.0273 },
			{ 49629.8691, 11.1053 }, { 49637.9727, 11.1750 },
			{ 49651.5464, 11.2563 }, { 49660.9175, 11.1769 },
			{ 49669.7927, 11.1750 }, { 49680.2468, 10.9875 },
			{ 49689.7749, 10.7769 }, { 49699.3335, 10.0889 },
			{ 49710.6138, 9.6857 }, { 49720.2925, 9.2421 },
			{ 49731.7361, 8.9857 }, { 49741.0706, 8.9250 },
			{ 49749.7312, 8.9600 }, { 49761.4399, 8.6100 },
			{ 49769.2483, 8.6000 }, { 49779.5640, 8.2579 },
			{ 49787.6467, 8.2385 }, { 49798.9131, 8.1429 },
			{ 49809.2349, 8.3417 }, { 49819.2400, 8.2400 },
			{ 49831.4666, 8.3429 }, { 49838.8413, 8.6000 },
			{ 49848.1001, 8.5000 }, { 49859.1162, 8.3500 },
			{ 49869.3145, 8.2545 }, { 49884.3999, 8.0000 },
			{ 49890.9819, 8.1500 }, { 49899.2625, 8.1750 },
			{ 49908.5847, 8.2417 }, { 49921.4946, 8.2407 },
			{ 49930.2036, 8.3964 }, { 49939.3235, 8.5059 },
			{ 49950.3618, 8.7550 }, { 49960.1179, 8.8882 },
			{ 49969.6931, 9.1438 }, { 49979.2898, 9.4280 },
			{ 49990.1238, 9.6727 }, { 50000.3779, 10.0760 },
			{ 50010.7258, 10.2700 }, { 50019.8032, 10.5357 },
			{ 50029.6235, 10.9214 }, { 50040.5103, 11.1619 },
			{ 50048.7349, 11.2937 }, { 50059.4429, 11.3571 },
			{ 50069.6663, 11.6182 }, { 50078.0090, 11.5417 },
			{ 50089.7605, 11.4917 }, { 50098.0664, 11.4944 },
			{ 50109.4731, 11.2417 }, { 50121.0239, 11.0556 },
			{ 50131.1040, 11.0667 }, { 50139.8784, 10.6273 },
			{ 50150.3179, 10.2450 }, { 50159.2695, 9.9667 },
			{ 50170.2480, 9.1909 }, { 50179.4995, 8.5750 },
			{ 50188.5771, 8.3400 }, { 50197.8154, 8.1444 },
			{ 50210.5557, 8.1600 }, { 50223.5605, 7.9750 },
			{ 50230.5376, 8.1333 }, { 50240.6533, 8.1091 },
			{ 50249.4692, 8.3353 }, { 50260.2563, 8.1667 },
			{ 50270.1099, 8.4375 }, { 50279.5513, 8.5179 },
			{ 50289.0376, 8.5000 }, { 50300.3882, 8.5586 },
			{ 50310.4990, 8.6040 }, { 50320.5752, 8.4636 },
			{ 50331.1274, 8.3238 }, { 50340.0112, 8.4346 },
			{ 50348.2881, 8.3526 }, { 50357.0029, 8.1714 } };

}

package org.cloudbus.cloudsim.power;

import java.util.Comparator;


import org.cloudbus.cloudsim.power.PowerHost;
public class AvailabeHostPowerComparator implements Comparator<PowerHost> {

	@Override
	public int compare(PowerHost o1, PowerHost o2) {
	   	 Double AvailablePowero1 = PowerHost.getAvailablePower(o1);
		  Double  AvailablePowero2 = PowerHost.getAvailablePower(o2);
		 return AvailablePowero2.compareTo(AvailablePowero1);
 
	}

}

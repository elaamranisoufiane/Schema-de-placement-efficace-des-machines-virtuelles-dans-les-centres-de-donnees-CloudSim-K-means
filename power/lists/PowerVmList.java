/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power.lists;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.math3.analysis.function.Min;
import org.apache.commons.math3.analysis.function.Power;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.lists.VmList;
import org.cloudbus.cloudsim.power.PowerVm;

/**
 * PowerVmList is a collection of operations on lists of power-enabled VMs.
 * 
 * <br/>If you are using any algorithms, policies or workload included in the power package please cite
 * the following paper:<br/>
 * 
 * <ul>
 * <li><a href="http://dx.doi.org/10.1002/cpe.1867">Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012</a>
 * </ul>
 * 
 * @author Anton Beloglazov
 * 
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 2.0
 * @todo It is a list, so it would be better inside the org.cloudbus.cloudsim.lists package.
 * This class in fact doesn't use a list or PowerVm, but a list of Vm.
 * The used methods are just of the Vm class, thus doesn't have
 * a reason to create another class. This classes don't either stores lists of VM,
 * they only perform operations on lists given by parameter.
 * So, the method of this class would be moved to the VmList class
 * and the class erased.
 */
public class PowerVmList extends VmList {

	/**
	 * Sort a given list of VMs by cpu utilization.
	 * 
	 * @param vmList the vm list to be sorted
	 */
	public static <T extends Vm> void sortByCpuUtilization(List<T> vmList) {
		Collections.sort(vmList, new Comparator<T>() {

			@Override
			public int compare(T a, T b) throws ClassCastException {
				Double aUtilization = a.getTotalUtilizationOfCpuMips(CloudSim.clock());
				Double bUtilization = b.getTotalUtilizationOfCpuMips(CloudSim.clock());
				return bUtilization.compareTo(aUtilization);
			}
		});
	}

	public static List<List<PowerVm>> returnCluster(List<? extends Vm> vmsToMigrate, double[][] centroids) {
		ArrayList<ArrayList<Double>> EuclideanDistance = new ArrayList<ArrayList<Double>>();
		//System.out.println(centroids);
		for (int icentroid = 0; icentroid < centroids.length; icentroid++) {
			ArrayList<Double> EuclideanDistanceTemp = new ArrayList<Double>();
		for (int i = 0; i < vmsToMigrate.size(); i++) {
			EuclideanDistanceTemp.add(Math.sqrt(Math.pow((centroids[icentroid][0]-vmsToMigrate.get(i).getMips()), 2)+Math.pow((centroids[icentroid][1]-vmsToMigrate.get(i).getRam()), 2)));		
		}			
	EuclideanDistance.add(EuclideanDistanceTemp);  
		} 
		for (int i = 0; i < EuclideanDistance.size(); i++) {
			System.out.println(EuclideanDistance.get(i));
		}	
		 int e[] = new int[vmsToMigrate.size()];
 	 for (int ivm = 0; ivm < vmsToMigrate.size(); ivm++) {
 		double MinDist = Double.MAX_VALUE;
 		int indiceMin1 = 0;
 		
 		for (int icluster = 0; icluster < centroids.length; icluster++) {	
 			if(EuclideanDistance.get(icluster).get(ivm) < MinDist)
 			{
	        	 MinDist= EuclideanDistance.get(icluster).get(ivm);	
 			  indiceMin1 = icluster;
 			}
		}		 
 		// System.out.print("-"+MinDist);
 		//System.out.print(indiceMin1);
 		e[ivm]=indiceMin1;
	}
  System.out.println();
  for (int i = 0; i < e.length; i++) {
	System.err.print(" "+e[i]);
}
  List<List<PowerVm>> test = new ArrayList<List<PowerVm>>() ;
       for (int icentroid = 0; icentroid < centroids.length; icentroid++) {
    	   ArrayList<PowerVm>  VmCluster = new ArrayList<PowerVm>();
  for (int imv = 0; imv < vmsToMigrate.size(); imv++) {
	if (e[imv] == icentroid) {
		VmCluster.add((PowerVm) vmsToMigrate.get(imv));
	}
   }
  Collections.addAll(test, VmCluster);
       } 
  
  for (int i = 0; i < test.size(); i++) {
	  System.out.println("taille cluster n :"+i);
	System.out.println(test.get(i).size());
}			
		
		return test;
	}

	public static <sortedVm extends PowerVm>  List<PowerVm> arrageByHighDensityCluster(List<List<PowerVm>> cluster, List<? extends Vm> vmsToMigrate) {
	 	ArrayList<PowerVm> sortedVm = new ArrayList<PowerVm>(); 
	    ArrayList<Integer> Density = new ArrayList<Integer>();    
	    if (cluster != null) {
		for (int icluster = 0; icluster < cluster.size(); icluster++) {
			Density.add(cluster.get(icluster).size());
		}	
		
	    Collections.sort(Density,Collections.reverseOrder());       
      for (int icluster = 0; icluster < cluster.size(); icluster++) {
    	  for (int iVmCluster = 0; iVmCluster < cluster.get(icluster).size(); iVmCluster++) {
				sortedVm.add(cluster.get(icluster).get(iVmCluster));
		}

      }	    
	  return sortedVm;
		 }
	    else {
	    	
			return (List<PowerVm>) vmsToMigrate;
		}
	    
	 }

	

}

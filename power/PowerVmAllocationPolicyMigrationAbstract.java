/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power;

import java.io.IOException;
import java.util.ArrayList;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.annotation.concurrent.ThreadSafe;

import org.apache.commons.math3.analysis.function.Power;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.HostDynamicWorkload;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.lists.HostList;
import org.cloudbus.cloudsim.power.lists.PowerVmList;
import org.cloudbus.cloudsim.util.ExecutionTimeMeasurer;
 


/**
 * An abstract power-aware VM allocation policy that dynamically optimizes the VM
 * allocation (placement) using migration.
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
 * @since CloudSim Toolkit 3.0
 */
public abstract class PowerVmAllocationPolicyMigrationAbstract extends PowerVmAllocationPolicyAbstract {

	/** The vm selection policy. */
	private PowerVmSelectionPolicy vmSelectionPolicy;

	/** A list of maps between a VM and the host where it is place.
         * @todo This list of map is implemented in the worst way.
         * It should be used just a Map<Vm, Host> to find out 
         * what PM is hosting a given VM.
         */

	
	private final List<Map<String, Object>> savedAllocation = new ArrayList<Map<String, Object>>();

	/** A map of CPU utilization history (in percentage) for each host,
         where each key is a host id and each value is the CPU utilization percentage history.*/
	private final Map<Integer, List<Double>> utilizationHistory = new HashMap<Integer, List<Double>>();

	/** 
         * The metric history. 
         * @todo the map stores different data. Sometimes it stores the upper threshold,
         * other it stores utilization threshold or predicted utilization, that
         * is very confusing.
         */
	private final Map<Integer, List<Double>> metricHistory = new HashMap<Integer, List<Double>>();

	/** The time when entries in each history list was added. 
         * All history lists are updated at the same time.
         */
	private final Map<Integer, List<Double>> timeHistory = new HashMap<Integer, List<Double>>();

	/** The history of time spent in VM selection 
         * every time the optimization of VM allocation method is called. 
         * @see #optimizeAllocation(java.util.List) 
         */
	private final List<Double> executionTimeHistoryVmSelection = new LinkedList<Double>();

	/** The history of time spent in host selection 
         * every time the optimization of VM allocation method is called. 
         * @see #optimizeAllocation(java.util.List) 
         */
	private final List<Double> executionTimeHistoryHostSelection = new LinkedList<Double>();

	/** The history of time spent in VM reallocation 
         * every time the optimization of VM allocation method is called. 
         * @see #optimizeAllocation(java.util.List) 
         */
	private final List<Double> executionTimeHistoryVmReallocation = new LinkedList<Double>();

	/** The history of total time spent in every call of the 
         * optimization of VM allocation method. 
         * @see #optimizeAllocation(java.util.List) 
         */
	private final List<Double> executionTimeHistoryTotal = new LinkedList<Double>();

	private int k;

	/**
	 * Instantiates a new PowerVmAllocationPolicyMigrationAbstract.
	 * 
	 * @param hostList the host list
	 * @param vmSelectionPolicy the vm selection policy
	 */
	public PowerVmAllocationPolicyMigrationAbstract(
			List<? extends Host> hostList,
			PowerVmSelectionPolicy vmSelectionPolicy) {
		super(hostList);
		setVmSelectionPolicy(vmSelectionPolicy);
	}
	
	/**
	 * Optimize allocation of the VMs according to current utilization.
	 * 
	 * @param vmList the vm list
	 * 
	 * @return the array list< hash map< string, object>>
	 */
	@Override
	public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
		ExecutionTimeMeasurer.start("optimizeAllocationTotal");

		ExecutionTimeMeasurer.start("optimizeAllocationHostSelection");
		List<PowerHostUtilizationHistory> overUtilizedHosts = getOverUtilizedHosts();
		getExecutionTimeHistoryHostSelection().add(
				ExecutionTimeMeasurer.end("optimizeAllocationHostSelection"));

		printOverUtilizedHosts(overUtilizedHosts);

		saveAllocation();

		ExecutionTimeMeasurer.start("optimizeAllocationVmSelection");
		List<? extends Vm> vmsToMigrate = getVmsToMigrateFromHosts(overUtilizedHosts);
		getExecutionTimeHistoryVmSelection().add(ExecutionTimeMeasurer.end("optimizeAllocationVmSelection"));

		Log.printLine("Reallocation of VMs from the over-utilized hosts:");
		ExecutionTimeMeasurer.start("optimizeAllocationVmReallocation");
		List<Map<String, Object>> migrationMap = getNewVmPlacement(vmsToMigrate, new HashSet<Host>(
				overUtilizedHosts));
		getExecutionTimeHistoryVmReallocation().add(
				ExecutionTimeMeasurer.end("optimizeAllocationVmReallocation"));
		Log.printLine();

		migrationMap.addAll(getMigrationMapFromUnderUtilizedHosts(overUtilizedHosts));

		restoreAllocation();

		getExecutionTimeHistoryTotal().add(ExecutionTimeMeasurer.end("optimizeAllocationTotal"));

		return migrationMap;
	}

	/**
	 * Gets the migration map from under utilized hosts.
	 * 
	 * @param overUtilizedHosts the over utilized hosts
	 * @return the migration map from under utilized hosts
	 */
	protected List<Map<String, Object>> getMigrationMapFromUnderUtilizedHosts(
			List<PowerHostUtilizationHistory> overUtilizedHosts) {
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
		List<PowerHost> switchedOffHosts = getSwitchedOffHosts();

		// over-utilized hosts + hosts that are selected to migrate VMs to from over-utilized hosts
		Set<PowerHost> excludedHostsForFindingUnderUtilizedHost = new HashSet<PowerHost>();
		excludedHostsForFindingUnderUtilizedHost.addAll(overUtilizedHosts);
		excludedHostsForFindingUnderUtilizedHost.addAll(switchedOffHosts);
		excludedHostsForFindingUnderUtilizedHost.addAll(extractHostListFromMigrationMap(migrationMap));

		// over-utilized + under-utilized hosts
		Set<PowerHost> excludedHostsForFindingNewVmPlacement = new HashSet<PowerHost>();
		excludedHostsForFindingNewVmPlacement.addAll(overUtilizedHosts);
		excludedHostsForFindingNewVmPlacement.addAll(switchedOffHosts);

		int numberOfHosts = getHostList().size();

		while (true) {
			if (numberOfHosts == excludedHostsForFindingUnderUtilizedHost.size()) {
				break;
			}

			PowerHost underUtilizedHost = getUnderUtilizedHost(excludedHostsForFindingUnderUtilizedHost);
			if (underUtilizedHost == null) {
				break;
			}

			Log.printConcatLine("Under-utilized host: host #", underUtilizedHost.getId(), "\n");

			excludedHostsForFindingUnderUtilizedHost.add(underUtilizedHost);
			excludedHostsForFindingNewVmPlacement.add(underUtilizedHost);

			List<? extends Vm> vmsToMigrateFromUnderUtilizedHost = getVmsToMigrateFromUnderUtilizedHost(underUtilizedHost);
			if (vmsToMigrateFromUnderUtilizedHost.isEmpty()) {
				continue;
			}

			Log.print("Reallocation of VMs from the under-utilized host: ");
			if (!Log.isDisabled()) {
				for (Vm vm : vmsToMigrateFromUnderUtilizedHost) {
					Log.print(vm.getId() + " ");
				}
			}
			Log.printLine();

			List<Map<String, Object>> newVmPlacement = getNewVmPlacementFromUnderUtilizedHost(
					vmsToMigrateFromUnderUtilizedHost,
					excludedHostsForFindingNewVmPlacement);

			excludedHostsForFindingUnderUtilizedHost.addAll(extractHostListFromMigrationMap(newVmPlacement));

			migrationMap.addAll(newVmPlacement);
			Log.printLine();
		}

		return migrationMap;
	}

	/**
	 * Prints the over utilized hosts.
	 * 
	 * @param overUtilizedHosts the over utilized hosts
	 */
	protected void printOverUtilizedHosts(List<PowerHostUtilizationHistory> overUtilizedHosts) {
		if (!Log.isDisabled()) {
			Log.printLine("Over-utilized hosts:");
			for (PowerHostUtilizationHistory host : overUtilizedHosts) {
				Log.printConcatLine("Host #", host.getId());
			}
			Log.printLine();
		}
	}

	/**
	 * Finds a PM that has enough resources to host a given VM
         * and that will not be overloaded after placing the VM on it.
         * The selected host will be that one with most efficient
         * power usage for the given VM.
	 * 
	 * @param vm the VM
	 * @param excludedHosts the excluded hosts
	 * @return the host found to host the VM
	 */
	public PowerHost findHostForVm(Vm vm, Set<? extends Host> excludedHosts) {
		
		
		  double minPower = Double.MAX_VALUE;
		 
		PowerHost allocatedHost = null;

		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (excludedHosts.contains(host)) {
				continue;
			}
			if (host.isSuitableForVm(vm)) {
				if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
					continue;
				}

				try {
					double powerAfterAllocation = getPowerAfterAllocation(host, vm);
					if (powerAfterAllocation != -1) {
						double powerDiff = powerAfterAllocation - host.getPower();
						if (powerDiff < minPower) {
							minPower = powerDiff;
							allocatedHost = host;
						}
					}
				} catch (Exception e) {
				}
			}
		}
		return allocatedHost;
		
	}
////////////////////////////////////
	public PowerHost findHostForVmPABFD(Vm vm, Set<? extends Host> excludedHosts) {
		double minPower = Double.MAX_VALUE;
		PowerHost allocatedHost = null;

		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (excludedHosts.contains(host)) {
				continue;
			}
			if (host.isSuitableForVm(vm)) {
				if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
					continue;
				}

				try {
					double powerAfterAllocation = getPowerAfterAllocation(host, vm);
					if (powerAfterAllocation != -1) {
						double powerDiff = powerAfterAllocation - host.getPower();
						if (powerDiff < minPower) {
							minPower = powerDiff;
							allocatedHost = host;
						}
					}
				} catch (Exception e) {
				}
			}
		}
		return allocatedHost;
	
	}
	//MWFDVP
	public PowerHost findHostForVmMWFDVP(Vm vm, Set<? extends Host> excludedHosts) {
		 
		 double maxPower = Double.MIN_VALUE;
			PowerHost allocatedHost = null;

			for (PowerHost host : this.<PowerHost> getHostList()) {
				if (excludedHosts.contains(host)) {
					continue;
				}
				if (host.isSuitableForVm(vm)) {
					if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
						continue;
					}

					try {
						double powerAfterAllocation = getPowerAfterAllocation(host, vm);
						if (powerAfterAllocation != -1) {
							double powerDiff = powerAfterAllocation - host.getPower();
							if (powerDiff > maxPower) {
								maxPower = powerDiff;
								allocatedHost = host;
							}
						}
					} catch (Exception e) {
					}
				}
			}
			return allocatedHost;

	}
	//SWFDVP
	public PowerHost findHostForVmSWFDVP(Vm vm, Set<? extends Host> excludedHosts) {
		double maxPower = Double.MIN_VALUE;
		PowerHost allocatedHost = null;
		PowerHost secondHost = null;

		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (excludedHosts.contains(host)) {
				continue;
			}
			if (host.isSuitableForVm(vm)) {
				if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
					continue;
				}

				try {
					double powerAfterAllocation = getPowerAfterAllocation(host, vm);
					if (powerAfterAllocation != -1) {
						double powerDiff = powerAfterAllocation - host.getPower();
						if (powerDiff > maxPower) {
							maxPower = powerDiff;
							
							if (allocatedHost != null) secondHost = allocatedHost; 
							allocatedHost=host;
						}
					}
				} catch (Exception e) {
				}
			}
			
			if(secondHost!=null)return secondHost;
		}
		
		return allocatedHost;
		
	}
	//FFDHDVP
	public PowerHost findHostForVmFFDHDVP(Vm vm, Set<? extends Host> excludedHosts) {
		sortHostsByAvailablePowerDecreasing();
		PowerHost allocatedHost = null;
			for (PowerHost host : this.<PowerHost> getHostList()) {
				if (excludedHosts.contains(host)) {
					continue;
				}
				if (host.isSuitableForVm(vm)) {
					if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
						continue;
					}

					try {
						double powerAfterAllocation = getPowerAfterAllocation(host, vm);
						if (powerAfterAllocation < host.getMaxPower()) {
						 allocatedHost = host; 
						}
					} catch (Exception e) {
					}
				}
			}
			return allocatedHost;
	}
	

///////////////////////////////////	
	//modified worst fit VM placement for clustering
	public PowerHost findHostForVmMWFVP_C(Vm vm, Set<? extends Host> excludedHosts) {
		 
		 double maxPower = Double.MIN_VALUE;
			PowerHost allocatedHost = null;

			for (PowerHost host : this.<PowerHost> getHostList()) {
				if (excludedHosts.contains(host)) {
					continue;
				}
				if (host.isSuitableForVm(vm)) {
					if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
						continue;
					}

					try {
						double powerAfterAllocation = getPowerAfterAllocation(host, vm);
						if (powerAfterAllocation != -1) {
							double powerDiff = powerAfterAllocation - host.getPower();
							if (powerDiff > maxPower) {
								maxPower = powerDiff;
								allocatedHost = host;
							}
						}
					} catch (Exception e) {
					}
				}
			}
			return allocatedHost;

	}
	
	
	
/////////////////////////////////	
	/**
	 * Checks if a host will be over utilized after placing of a candidate VM.
	 * 
	 * @param host the host to verify
	 * @param vm the candidate vm 
	 * @return true, if the host will be over utilized after VM placement; false otherwise
	 */
	protected boolean isHostOverUtilizedAfterAllocation(PowerHost host, Vm vm) {
		boolean isHostOverUtilizedAfterAllocation = true;
		if (host.vmCreate(vm)) {
			isHostOverUtilizedAfterAllocation = isHostOverUtilized(host);
			host.vmDestroy(vm);
		}
		return isHostOverUtilizedAfterAllocation;
	}

	@Override
	public PowerHost findHostForVm(Vm vm) {
		Set<Host> excludedHosts = new HashSet<Host>();
		if (vm.getHost() != null) {
			excludedHosts.add(vm.getHost());
		}
		return findHostForVmFFDHDVP(vm, excludedHosts);
	}

	/**
	 * Extracts the host list from a migration map.
	 * 
	 * @param migrationMap the migration map
	 * @return the list
	 */
	protected List<PowerHost> extractHostListFromMigrationMap(List<Map<String, Object>> migrationMap) {
		List<PowerHost> hosts = new LinkedList<PowerHost>();
		for (Map<String, Object> map : migrationMap) {
			hosts.add((PowerHost) map.get("host"));
		}
		return hosts;
	}

	/**
	 * Gets a new vm placement considering the list of VM to migrate.
	 * 
	 * @param vmsToMigrate the list of VMs to migrate
	 * @param excludedHosts the list of hosts that aren't selected as destination hosts
	 * @return the new vm placement map
	 */
	protected List<Map<String, Object>> getNewVmPlacement(
			List<? extends Vm> vmsToMigrate,
			Set<? extends Host> excludedHosts) {
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
	//	  PowerVmList.sortByCpuUtilization(vmsToMigrate);
		List<List<PowerVm>> cluster=null;
		//Algorithme k-means
        cluster=MK(excludedHosts,vmsToMigrate);
        System.out.println(vmsToMigrate);
	    vmsToMigrate = PowerVmList.arrageByHighDensityCluster(cluster,vmsToMigrate);
	  //   System.out.println(vmsToMigrate);
		 // c'est une pause de 1000ms pour observer les résultats
	//  System.out.println(vmsToMigrate);
	    
	    try {
				Thread.sleep(0);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		  
	   		for (Vm vm : vmsToMigrate) {
			PowerHost allocatedHost = findHostForVmFFDHDVP(vm, excludedHosts);
			if (allocatedHost != null) {
				allocatedHost.vmCreate(vm);
				Log.printConcatLine("VM #", vm.getId(), " allocated to host #", allocatedHost.getId());
				Map<String, Object> migrate = new HashMap<String, Object>();
				migrate.put("vm", vm);
				migrate.put("host", allocatedHost);
				migrationMap.add(migrate);
			}	
		}
		return migrationMap;
	}
	/*    
    centroidss[0][0]=2500;centroidss[0][1]=870;
    centroidss[1][0]=2000; centroidss[1][1]=1740;
    centroidss[2][0]=1000; centroidss[2][1]=1740;
    centroidss[2][0]=500; centroidss[2][1]=613;
    */

/*
 *    
        centroidss[0][0]=2500;centroidss[0][1]=870;
        centroidss[1][0]=2000; centroidss[1][1]=1740;
        centroidss[2][0]=1000; centroidss[2][1]=1740;
        centroidss[2][0]=500; centroidss[2][1]=613;
          
          
 * */
	protected   List<List<PowerVm>> MK(Set<? extends Host> excludedHosts,List<? extends Vm> vmsToMigrate) {
		//nombre  initiale des clusters
		int  k = find_numberof_cluster(excludedHosts,vmsToMigrate);
		System.out.println(" find_numberof_cluster ");
		 List<List<PowerVm>> Clusters = null ;
		System.out.println("Nombre des clusters  =====  "+k);
		
	
		
		if (k != -1) {
		}
		//centroide initial 
		     if (k > 2) {
        double centroidss[][]=new double[k][2];
        centroidss =find_init_centroids(vmsToMigrate,k); 
		System.out.println("**************************************");
	 //	try {
			double[] mips = new double[vmsToMigrate.size()];
			double[] ram = new double[vmsToMigrate.size()];
			for (int i = 0; i < vmsToMigrate.size(); i++) {
				mips[i]=(double) vmsToMigrate.get(i).getMips();
				ram[i]=(double) vmsToMigrate.get(i).getRam();
			}
			 
			for (int i = 0; i < vmsToMigrate.size(); i++) {
				System.out.print(" "+vmsToMigrate.get(i).getMips()); 
				System.out.print(" "+vmsToMigrate.get(i).getRam()); 
			}
			
		// visualiser les résultats dans un graphe
		//	 easyjcckit.QuickPlot.scatter(ram , mips );  
			// easyjcckit.QuickPlot.addScatter(ram, mips);
		//	System.out.println("Press enter to exit");
		//	System.in.read();
		//} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		//}
			// affichage des centroides 
		System.out.println("**************************************");
	 	for (int i = 0; i < k; i++) {
		 	System.err.println("centroids  :  "+centroidss[i][0] +"centroids  :  "+centroidss[i][1]); 
	 	}
	 	System.out.println("***************************************");
	       try {
				Thread.sleep(0);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

	 	double Lastcentroidss[][]=new double[k][2];
	 for (int U = 0; U < 10; U++)  {
		 System.out.println("// //// //// //// iter "+U+1); 
		   Clusters =PowerVmList.returnCluster(vmsToMigrate,centroidss);
		 Lastcentroidss = centroidss;
	 		centroidss=getNewCentroids(Clusters);
	 		System.out.println("--------------------------------------");
		 	for (int i = 0; i < k; i++) {
	 			 	System.out.println("centroids  :  "+centroidss[i][0] +"centroids  :  "+centroidss[i][1]); 
	 		 	}
	 		System.out.println("--------------------------------------");
	 		  try {
					Thread.sleep(0);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

	 		if (egal(Lastcentroidss, centroidss)) {
	 			System.err.println("voila le resultats ===> ");
	 			try {
	 				Thread.sleep(0);
	 			} catch (InterruptedException e) {
	 				// TODO Auto-generated catch block
	 				e.printStackTrace();
	 			}
				break;	}}
	 //while(true);
	 }	
		     
		    
		     
	return (List<List<PowerVm>>) Clusters;
	}

	
	public static boolean egal(double[][] lastcentroidss,double[][] centroidss){
		boolean test=true;
		for(int y=0; y<lastcentroidss.length; y++)
			for (int i = 0; i < lastcentroidss[y].length; i++) {
				 if(centroidss[y][i]!=lastcentroidss[y][i]) test=false;
			}
		   
		return test;
	    }

	private double[][] getNewCentroids(List<List<PowerVm>> clusters) {
	double [][]centroid = new double[clusters.size()][2];
		double moyenRAM ,moyenMIPS;
		for (int icluster = 0; icluster < clusters.size(); icluster++) {
			moyenRAM =0.0;
			moyenMIPS= 0.0;
			for (int vmcluster = 0; vmcluster < clusters.get(icluster).size(); vmcluster++) {
				moyenMIPS+=clusters.get(icluster).get(vmcluster).getMips();
				moyenRAM+=clusters.get(icluster).get(vmcluster).getRam();
			}
			centroid[icluster][0]=(moyenMIPS/clusters.get(icluster).size());
			centroid[icluster][1]=(moyenRAM/clusters.get(icluster).size());
		
		}
				 
	return centroid;
}

	private double[][]  find_init_centroids(List<? extends Vm> vmsToMigrate, int k) {
		ArrayList< Integer> centroids = new ArrayList<Integer>();
		ArrayList<Double> EuclideanDistance = new ArrayList<Double>();
		ArrayList<Double> ClustersMIPS = new ArrayList<Double>();
		ArrayList<Double> ClusterRAM = new ArrayList<Double>(); 
	//premiere centroide
		float size=vmsToMigrate.size();
		int indice=0;
		 double MIPS1=0,RAM1=0;
		for (int i = 0; i < size; i++) {
			MIPS1+=vmsToMigrate.get(i).getMips();
			RAM1+=vmsToMigrate.get(i).getRam();
		}
		 ClustersMIPS.add((Double)MIPS1/vmsToMigrate.size());
		ClusterRAM.add((Double)RAM1/vmsToMigrate.size());
		 centroids.add(indice);
		 System.err.println("ram "+ClusterRAM+" Mips  "+ClustersMIPS);
		 for (int icentroid = 0; icentroid < k-1; icentroid++) {
			 
			 EuclideanDistance.clear();
			 ///moyenne
			 double MIPS=0,RAM=0;
			 for (int i = 0; i < ClusterRAM.size(); i++) {
			  MIPS+=ClustersMIPS.get(i);
			  RAM+=ClusterRAM.get(i);
			}
			double AverageMIPS=MIPS/ClustersMIPS.size(); 
			double AverageRAM=RAM/ClusterRAM.size();
			System.out.println("average MIPS "+AverageMIPS +" average Ram"+ AverageRAM);
				//calclul de distance euclidienne entre le premiere centroide et les  autres 
				for (int i = 0; i < vmsToMigrate.size(); i++) {
					EuclideanDistance.add(Math.sqrt(Math.pow((AverageMIPS-vmsToMigrate.get(i).getMips()), 2)+Math.pow((AverageRAM-vmsToMigrate.get(i).getRam()), 2)));	 
					}
				double MaxDistEcl = EuclideanDistance.get(0);
				//maximun des distances euclideans
				for(int i = 0; i < EuclideanDistance.size(); i++){
					 if (inArray(vmsToMigrate.get(i).getMips(), ClustersMIPS) &&  inArray(vmsToMigrate.get(i).getRam(), ClusterRAM)) {
						continue;
					}
					else {
						if(EuclideanDistance.get(i) > MaxDistEcl)
				        	 MaxDistEcl= EuclideanDistance.get(i);
					 }   }
				indice=EuclideanDistance.indexOf(MaxDistEcl);
				System.out.println("dist : "+EuclideanDistance+"\n max dist :" +EuclideanDistance.indexOf(MaxDistEcl)+"\n indice :" +indice);
				ClustersMIPS.add(vmsToMigrate.get(indice).getMips());
				ClusterRAM.add((double) vmsToMigrate.get(indice).getRam());
				 System.out.println("rams and mips "+ClusterRAM +"   ;;;;;;  "+ClustersMIPS);
				System.err.println(indice);
			centroids.add(indice);			
		}
		   
		 double centroidss[][]=new double[centroids.size()][2];
		 centroidss[0][0]=ClustersMIPS.get(0);
	     centroidss[0][1]=ClusterRAM.get(0);
		for (int i = 1; i < centroids.size(); i++) {
			centroidss[i][0]=vmsToMigrate.get(centroids.get(i)).getMips();
			centroidss[i][1]=vmsToMigrate.get(centroids.get(i)).getRam();
		}
	return centroidss;
}
	
	
	public static boolean inArray(double value, ArrayList<Double> clustersMIPS)
	{
	     for(int i=0;i<clustersMIPS.size();i++)
	    {
	        if(clustersMIPS.get(i) == value){return true;}
	    }
	    return false;
	}

	protected  int find_numberof_cluster(Set<? extends Host> excludedHosts,List<? extends Vm> vmsToMigrate) {
		double AlphaMaxAvailableCpuMip = Double.MIN_VALUE;
		double BetaMinAvailableCpuMip = Double.MAX_VALUE;
		double GamaMaxCurrentAllocatedCpuMips= Double.MIN_VALUE;
		double DeltaMinCurrentAllocatedCpuMips= Double.MAX_VALUE;
		ArrayList<Double>  CurrentCpuMipsParHost= new ArrayList<Double>();
		ArrayList<Double>  CurrentCpuMipsParVm= new ArrayList<Double>();
		 if (vmsToMigrate.size() > 2) {
		for (PowerHost host : this.<PowerHost> getHostList()) {
      		CurrentCpuMipsParHost.add(host.getAvailableMips());
		}
		for (Vm vm : vmsToMigrate) {
			CurrentCpuMipsParVm.add(vm.getMips()); 
		} 
	if (CurrentCpuMipsParHost != null && CurrentCpuMipsParVm != null) {
		for(int i = 0; i < CurrentCpuMipsParHost.size(); i++){
	         if(CurrentCpuMipsParHost.get(i) < BetaMinAvailableCpuMip)
	        	 BetaMinAvailableCpuMip= CurrentCpuMipsParHost.get(i);
	         if(CurrentCpuMipsParHost.get(i) > AlphaMaxAvailableCpuMip)
	        	 AlphaMaxAvailableCpuMip = CurrentCpuMipsParHost.get(i);
	       }
	       for(int i = 0; i < CurrentCpuMipsParVm.size(); i++){
		         if(CurrentCpuMipsParVm.get(i) < DeltaMinCurrentAllocatedCpuMips)
		        	 DeltaMinCurrentAllocatedCpuMips= CurrentCpuMipsParVm.get(i);
		         if(CurrentCpuMipsParVm.get(i) > GamaMaxCurrentAllocatedCpuMips)
		        	 GamaMaxCurrentAllocatedCpuMips = CurrentCpuMipsParVm.get(i);
		       }
	      int maxpoint = (int)(AlphaMaxAvailableCpuMip/DeltaMinCurrentAllocatedCpuMips); 
	      int minpoint = (int)(BetaMinAvailableCpuMip/GamaMaxCurrentAllocatedCpuMips);
		System.out.println("_______________________________________");
	      System.out.println(CurrentCpuMipsParHost);
	      System.out.println(CurrentCpuMipsParVm);
	    System.out.println("_______________________________________");
	      //if (((int)(maxpoint+minpoint)/2) > 2) {
			 return ((int)((maxpoint+minpoint)/2)-1);
	    //  return 3;
	}else {
		  return 0;}	
		 }else {return -1;}}

	
	
	
	/**
	 * Gets the new vm placement from under utilized host.
	 * @param list 
	 * 
	 * @param vmsToMigrate the list of VMs to migrate
	 * @param excludedHosts the list of hosts that aren't selected as destination hosts
	 * @return the new vm placement from under utilized host
	 */
	protected  static boolean inZero(List<List<PowerVm>> list) {
		boolean test = false;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).size() == 0 ) {
				test=true;
			}
		}
		
		
		return test;
	}
	
	
	protected List<Map<String, Object>> getNewVmPlacementFromUnderUtilizedHost(
			List<? extends Vm> vmsToMigrate,
			Set<? extends Host> excludedHosts) {
		List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
	//	 PowerVmList.sortByCpuUtilization(vmsToMigrate);
  	List<List<PowerVm>> cluster=null;
 	cluster=MK(excludedHosts,vmsToMigrate);
 	 vmsToMigrate = PowerVmList.arrageByHighDensityCluster(cluster,vmsToMigrate);
			 
		 //if (vmsToMigrate.size() > 5 && !inZero(PowerVmList.returnCluster(vmsToMigrate,find_init_centroids(vmsToMigrate, find_numberof_cluster(excludedHosts, vmsToMigrate))))) {
 /*
		if (vmsToMigrate != null && excludedHosts != null) {
			
		List<List<PowerVm>> cluster;
		cluster=MK(excludedHosts,vmsToMigrate);
	 vmsToMigrate = PowerVmList.arrageByHighDensityCluster(cluster,vmsToMigrate);
		 
		 try {
				Thread.sleep(0);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	 	 }
	 	
 */
		for (Vm vm : vmsToMigrate) {
			PowerHost allocatedHost = findHostForVmMWFDVP(vm, excludedHosts);
			if (allocatedHost != null) {
				allocatedHost.vmCreate(vm);
				Log.printConcatLine("VM #", vm.getId(), " allocated to host #", allocatedHost.getId());

				Map<String, Object> migrate = new HashMap<String, Object>();
				migrate.put("vm", vm);
				migrate.put("host", allocatedHost);
				migrationMap.add(migrate);
			} else {
				Log.printLine("Not all VMs can be reallocated from the host, reallocation cancelled");
				for (Map<String, Object> map : migrationMap) {
					((Host) map.get("host")).vmDestroy((Vm) map.get("vm"));
				}
				migrationMap.clear();
				break;
			}
		}
		return migrationMap;
	}

	/**
	 * Gets the VMs to migrate from hosts.
	 * 
	 * @param overUtilizedHosts the over utilized hosts
	 * @return the VMs to migrate from hosts
	 */
	protected List<? extends Vm>
	  getVmsToMigrateFromHosts(List<PowerHostUtilizationHistory> overUtilizedHosts) {
		List<Vm> vmsToMigrate = new LinkedList<Vm>();
		for (PowerHostUtilizationHistory host : overUtilizedHosts) {
			while (true) {
				Vm vm = getVmSelectionPolicy().getVmToMigrate(host);
				if (vm == null) {
					break;
				}
				vmsToMigrate.add(vm);
				host.vmDestroy(vm);
				if (!isHostOverUtilized(host)) {
					break;
				}
			}
		}
		return vmsToMigrate;
	}

	/**
	 * Gets the VMs to migrate from under utilized host.
	 * 
	 * @param host the host
	 * @return the vms to migrate from under utilized host
	 */
	protected List<? extends Vm> getVmsToMigrateFromUnderUtilizedHost(PowerHost host) {
		List<Vm> vmsToMigrate = new LinkedList<Vm>();
		for (Vm vm : host.getVmList()) {
			if (!vm.isInMigration()) {
				vmsToMigrate.add(vm);
			}
		}
		return vmsToMigrate;
	}

	/**
	 * Gets the over utilized hosts.
	 * 
	 * @return the over utilized hosts
	 */
	protected List<PowerHostUtilizationHistory> getOverUtilizedHosts() {
		List<PowerHostUtilizationHistory> overUtilizedHosts = new LinkedList<PowerHostUtilizationHistory>();
		for (PowerHostUtilizationHistory host : this.<PowerHostUtilizationHistory> getHostList()) {
			if (isHostOverUtilized(host)) {
				overUtilizedHosts.add(host);
			}
		}
		return overUtilizedHosts;
	}

	/**
	 * Gets the switched off hosts.
	 * 
	 * @return the switched off hosts
	 */
	protected List<PowerHost> getSwitchedOffHosts() {
		List<PowerHost> switchedOffHosts = new LinkedList<PowerHost>();
		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (host.getUtilizationOfCpu() == 0) {
				switchedOffHosts.add(host);
			}
		}
		return switchedOffHosts;
	}

	/**
	 * Gets the most under utilized host.
	 * 
	 * @param excludedHosts the excluded hosts
	 * @return the most under utilized host
	 */
	protected PowerHost getUnderUtilizedHost(Set<? extends Host> excludedHosts) {
		double minUtilization = 1;
		PowerHost underUtilizedHost = null;
		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (excludedHosts.contains(host)) {
				continue;
			}
			double utilization = host.getUtilizationOfCpu();
			if (utilization > 0 && utilization < minUtilization
					&& !areAllVmsMigratingOutOrAnyVmMigratingIn(host)) {
				minUtilization = utilization;
				underUtilizedHost = host;
			}
		}
		return underUtilizedHost;
	}

	/**
	 * Checks whether all VMs of a given host are in migration.
	 * 
	 * @param host the host
	 * @return true, if successful
	 */
	protected boolean areAllVmsMigratingOutOrAnyVmMigratingIn(PowerHost host) {
		for (PowerVm vm : host.<PowerVm> getVmList()) {
			if (!vm.isInMigration()) {
				return false;
			}
			if (host.getVmsMigratingIn().contains(vm)) {
				return true;
			}
		}
		return true;
	}

	/**
	 * Checks if host is over utilized.
	 * 
	 * @param host the host
	 * @return true, if the host is over utilized; false otherwise
	 */
	protected abstract boolean isHostOverUtilized(PowerHost host);

	/**
	 * Adds an entry for each history map of a host.
	 * 
	 * @param host the host to add metric history entries
	 * @param metric the metric to be added to the metric history map
	 */
	protected void addHistoryEntry(HostDynamicWorkload host, double metric) {
		int hostId = host.getId();
		if (!getTimeHistory().containsKey(hostId)) {
			getTimeHistory().put(hostId, new LinkedList<Double>());
		}
		if (!getUtilizationHistory().containsKey(hostId)) {
			getUtilizationHistory().put(hostId, new LinkedList<Double>());
		}
		if (!getMetricHistory().containsKey(hostId)) {
			getMetricHistory().put(hostId, new LinkedList<Double>());
		}
		if (!getTimeHistory().get(hostId).contains(CloudSim.clock())) {
			getTimeHistory().get(hostId).add(CloudSim.clock());
			getUtilizationHistory().get(hostId).add(host.getUtilizationOfCpu());
			getMetricHistory().get(hostId).add(metric);
		}
	}

	/**
	 * Updates the list of maps between a VM and the host where it is place.
         * @see #savedAllocation
	 */
	protected void saveAllocation() {
		getSavedAllocation().clear();
		for (Host host : getHostList()) {
			for (Vm vm : host.getVmList()) {
				if (host.getVmsMigratingIn().contains(vm)) {
					continue;
				}
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("host", host);
				map.put("vm", vm);
				getSavedAllocation().add(map);
			}
		}
	}

	/**
	 * Restore VM allocation from the allocation history.
         * @see #savedAllocation
	 */
	protected void restoreAllocation() {
		for (Host host : getHostList()) {
			host.vmDestroyAll();
			host.reallocateMigratingInVms();
		}
		for (Map<String, Object> map : getSavedAllocation()) {
			Vm vm = (Vm) map.get("vm");
			PowerHost host = (PowerHost) map.get("host");
			if (!host.vmCreate(vm)) {
				Log.printConcatLine("Couldn't restore VM #", vm.getId(), " on host #", host.getId());
				System.exit(0);
			}
			getVmTable().put(vm.getUid(), host);
		}
	}

	/**
	 * Gets the power consumption of a host after placement of a candidate VM.
         * The VM is not in fact placed at the host.
	 * 
	 * @param host the host
	 * @param vm the candidate vm
	 * 
	 * @return the power after allocation
	 */
	protected double getPowerAfterAllocation(PowerHost host, Vm vm) {
		double power = 0;
		try {
			power = host.getPowerModel().getPower(getMaxUtilizationAfterAllocation(host, vm));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		return power;
	}

	/**
	 * Gets the max power consumption of a host after placement of a candidate VM.
         * The VM is not in fact placed at the host.
         * We assume that load is balanced between PEs. The only
	 * restriction is: VM's max MIPS < PE's MIPS
	 * 
	 * @param host the host
	 * @param vm the vm
	 * 
	 * @return the power after allocation
	 */
	protected double getMaxUtilizationAfterAllocation(PowerHost host, Vm vm) {
		double requestedTotalMips = vm.getCurrentRequestedTotalMips();
		double hostUtilizationMips = getUtilizationOfCpuMips(host);
		double hostPotentialUtilizationMips = hostUtilizationMips + requestedTotalMips;
		double pePotentialUtilization = hostPotentialUtilizationMips / host.getTotalMips();
		return pePotentialUtilization;
	}
	
	/**
	 * Gets the utilization of the CPU in MIPS for the current potentially allocated VMs.
	 *
	 * @param host the host
	 *
	 * @return the utilization of the CPU in MIPS
	 */
	protected double getUtilizationOfCpuMips(PowerHost host) {
		double hostUtilizationMips = 0;
		for (Vm vm2 : host.getVmList()) {
			if (host.getVmsMigratingIn().contains(vm2)) {
				// calculate additional potential CPU usage of a migrating in VM
				hostUtilizationMips += host.getTotalAllocatedMipsForVm(vm2) * 0.9 / 0.1;
			}
			hostUtilizationMips += host.getTotalAllocatedMipsForVm(vm2);
		}
		return hostUtilizationMips;
	}

	/**
	 * Gets the saved allocation.
	 * 
	 * @return the saved allocation
	 */
	protected List<Map<String, Object>> getSavedAllocation() {
		return savedAllocation;
	}

	/**
	 * Sets the vm selection policy.
	 * 
	 * @param vmSelectionPolicy the new vm selection policy
	 */
	protected void setVmSelectionPolicy(PowerVmSelectionPolicy vmSelectionPolicy) {
		this.vmSelectionPolicy = vmSelectionPolicy;
	}

	/**
	 * Gets the vm selection policy.
	 * 
	 * @return the vm selection policy
	 */
	protected PowerVmSelectionPolicy getVmSelectionPolicy() {
		return vmSelectionPolicy;
	}

	/**
	 * Gets the utilization history.
	 * 
	 * @return the utilization history
	 */
	public Map<Integer, List<Double>> getUtilizationHistory() {
		return utilizationHistory;
	}

	/**
	 * Gets the metric history.
	 * 
	 * @return the metric history
	 */
	public Map<Integer, List<Double>> getMetricHistory() {
		return metricHistory;
	}

	/**
	 * Gets the time history.
	 * 
	 * @return the time history
	 */
	public Map<Integer, List<Double>> getTimeHistory() {
		return timeHistory;
	}

	/**
	 * Gets the execution time history vm selection.
	 * 
	 * @return the execution time history vm selection
	 */
	public List<Double> getExecutionTimeHistoryVmSelection() {
		return executionTimeHistoryVmSelection;
	}

	/**
	 * Gets the execution time history host selection.
	 * 
	 * @return the execution time history host selection
	 */
	public List<Double> getExecutionTimeHistoryHostSelection() {
		return executionTimeHistoryHostSelection;
	}

	/**
	 * Gets the execution time history vm reallocation.
	 * 
	 * @return the execution time history vm reallocation
	 */
	public List<Double> getExecutionTimeHistoryVmReallocation() {
		return executionTimeHistoryVmReallocation;
	}

	/**
	 * Gets the execution time history total.
	 * 
	 * @return the execution time history total
	 */
	public List<Double> getExecutionTimeHistoryTotal() {
		return executionTimeHistoryTotal;
	}
	public List<PowerHost> sortHostsByAvailablePowerDecreasing() {
		List<PowerHost> lst = this.getHostList();
		Collections.sort(lst, new AvailabeHostPowerComparator());
		return lst;}

}

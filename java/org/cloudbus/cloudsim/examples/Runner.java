package org.cloudbus.cloudsim.examples;

 
import java.io.FileNotFoundException;



import java.util.Calendar;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.power.Constants;
import org.cloudbus.cloudsim.examples.power.Helper;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.PowerHost;
import  org.cloudbus.cloudsim.examples.power.RunnerAbstract;

public class Runner {
	private static List<PowerHost> hosts;
	private static List<Vm> vms;
	private static List<Cloudlet> cloudlets;
	private static DatacenterBroker broker;
	private static PowerDatacenter dc;

	public static void main(String[] args) throws FileNotFoundException {

		int nbHosts = 800;
		int nbUsers = 50;
		int[] vmsNumber = { 1052, 898, 1061, 1516, 1078, 1463, 1358, 1233, 1054, 1033 };
		String[] inputFolders = { "20110303", "20110306", "20110309", "20110322", "20110325", "20110403", "20110409",
				"20110411", "20110412", "20110420" };
		String[] experimentName= new String[10];
		for (int i = 0; i <10; i++) {
			experimentName[i] = "exprForDay" + i + "_lrr_1.2_mmtd_"+inputFolders[i];
		}

		// initialiser cloudsim
		Calendar calendar = Calendar.getInstance();
		boolean traceFlag = false;
		CloudSim.init(nbUsers, calendar, traceFlag);
		////////////////////////////////////////////
		// creation des hosts
		hosts = Helper.createHostList(nbHosts);
		///////////////////////////////////////////
		// definition de la politique d'allocation des VMs
		String parameter = "1.2";
		int DayNumber=0;
		VmAllocationPolicy vmAllocPolicy = Helper.getVmAllocationPolicy("lr", "mmt", parameter, hosts);
		///////////////////////////////////////////dvfs , mmt
		// creation du datacenter
		dc = null;
		try {
			dc = (PowerDatacenter) Helper.createDatacenter("datacenter0", PowerDatacenter.class, hosts, vmAllocPolicy);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		///////////////////////////////////////////
		// create the broker
		broker = Helper.createBroker();
		int brokerId = broker.getId();
		///////////////////////////////////////////
		//Personalize simulation
		///////////////////////////////////////////
		// creation des Virtual Machines
		vms = Helper.createVmList(brokerId, vmsNumber[DayNumber]);
		broker.submitVmList(vms);
		///////////////////////////////////////////
		// creations des taches
		
		String inputFolderName = "C:/Users/elaam/OneDrive/Bureau/MonTravail/Cloud/cloudsim-5.0/cloudsim-5.0/modules/cloudsim-examples/src/main/resources/workload/planetlab/"+inputFolders[DayNumber];
		try {	
		cloudlets = Helper.createCloudletListPlanetLab(brokerId, inputFolderName);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		broker.submitCloudletList(cloudlets);
		
		// start the simulation************************
		dc.setDisableMigrations(false);
		CloudSim.terminateSimulation(Constants.SIMULATION_LIMIT);
	
		double lastClock = CloudSim.startSimulation();
		
		List<Cloudlet> newList = broker.getCloudletReceivedList();
		Log.printLine("Received " + newList.size() + " cloudlets");
		
		CloudSim.stopSimulation();
		
		String outputFolder = "C:/Users/elaam/OneDrive/Bureau/MonTravail/Cloud/cloudsim-5.0/cloudsim-5.0/modules/cloudsim-examples/src/main/resources/workload/planetlab/";
		Helper.printResults(dc, vms, lastClock, experimentName[DayNumber], Constants.OUTPUT_CSV, outputFolder);
		
	}


}


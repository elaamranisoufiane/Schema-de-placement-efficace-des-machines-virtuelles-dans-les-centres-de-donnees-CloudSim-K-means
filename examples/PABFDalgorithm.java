package org.cloudbus.cloudsim.examples;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.examples.CloudSimExample8.GlobalBroker;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

public class PABFDalgorithm {
	//liste des cloudlets
	private static List<Cloudlet> cloudletList;
	//liste des machines virtuelles 
	private static List<Vm> vmList;
	//la methode de createtion des machines V 
	private static List<Vm> createVM(int userId, int vms, int idShift) {
		//Crée un conteneur pour stocker les machines virtuelles. Cette liste est transmise au broker ultérieurement
		LinkedList<Vm> list = new LinkedList<Vm>();

		//les parametres des VMs 
		long size = 10000; //image size (MB)
		int ram = 512; //RAM
		int mips = 250;// mile instruction per second
		long bw = 1000;// bandwith : bande passante
		int pesNumber = 1; //numero de core (CPU)
		String vmm = "Xen"; //nome de machine virtuel

		//create VMs
		Vm[] vm = new Vm[vms];

		for(int i=0;i<vms;i++){
			vm[i] = new Vm(idShift + i, userId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerTimeShared());
			list.add(vm[i]);
		}

		return list;
	}
	
	//creation de cloudlet 
	private static List<Cloudlet> createCloudlet(int userId, int cloudlets, int idShift){
		// Creates a container to store Cloudlets
		LinkedList<Cloudlet> list = new LinkedList<Cloudlet>();

		//cloudlet parameters
		long length = 40000;
		long fileSize = 300;
		long outputSize = 300;
		int pesNumber = 1;
		UtilizationModel utilizationModel = new UtilizationModelFull();

		Cloudlet[] cloudlet = new Cloudlet[cloudlets];

		for(int i=0;i<cloudlets;i++){
			cloudlet[i] = new Cloudlet(idShift + i, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
			// setting the owner of these Cloudlets
			cloudlet[i].setUserId(userId);
			list.add(cloudlet[i]);
		}

		return list;
	}


	

	public static void main(String[] args) {
		Log.printLine("Starting PABFD Algorithm...");

		try {
			//premiere etape initialiser les cloudSim pachage 
			int num_user = 1;   // nombre d'utilisateurs
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false;  //  trace 

			// initialisation de biblio cloudsim
			CloudSim.init(num_user, calendar, trace_flag);

			GlobalBroker globalBroker = new GlobalBroker("GlobalBroker");

			// Second step: create datacenter
			//Datacenters are the resource providers in CloudSim. We need at list one of them to run a CloudSim simulation
			@SuppressWarnings("unused")
			Datacenter datacenter0 = createDatacenter("Datacenter_0");
			 
			

			//Third step: Create Broker
			DatacenterBroker broker = createBroker("Broker_0");
			int brokerId = broker.getId();

			//Fourth step: Create VMs and Cloudlets and send them to broker
			vmList = createVM(brokerId, 2, 0); //creating 5 vms
			cloudletList = createCloudlet(brokerId, 5, 0); // creating 10 cloudlets

			broker.submitVmList(vmList);
			broker.submitCloudletList(cloudletList);

			// Fifth step: Starts the simulation
			CloudSim.startSimulation();

			// Final step: Print results when simulation is over
			List<Cloudlet> newList = broker.getCloudletReceivedList();
			newList.addAll(globalBroker.getBroker().getCloudletReceivedList());

			CloudSim.stopSimulation();

			printCloudletList(newList);

			Log.printLine("Algorithm finished!");
		}
		catch (Exception e)
		{
			e.printStackTrace();
			Log.printLine(" error");
			//The simulation has been terminated due to an unexpected
		}

	}




private static Datacenter createDatacenter(String name){

	// Here are the steps needed to create a PowerDatacenter:
	// 1. We need to create a list to store one or more
	//    Machines
	List<Host> hostList = new ArrayList<Host>();

	// 2. A Machine contains one or more PEs or CPUs/Cores. Therefore, should
	//    create a list to store these PEs before creating
	//    a Machine.
	List<Pe> peList1 = new ArrayList<Pe>();

	int mips = 1000;

	// 3. Create PEs and add these into the list.
	//for a quad-core machine, a list of 4 PEs is required:
	peList1.add(new Pe(0, new PeProvisionerSimple(mips))); // need to store Pe id and MIPS Rating
	peList1.add(new Pe(1, new PeProvisionerSimple(mips)));

	//Another list, for a dual-core machine
	//List<Pe> peList2 = new ArrayList<Pe>();

	//peList2.add(new Pe(0, new PeProvisionerSimple(mips)));
	//peList2.add(new Pe(1, new PeProvisionerSimple(mips)));

	//4. Create Hosts with its id and list of PEs and add them to the list of machines
	int hostId=0;
	int ram = 16384; //host memory (MB)
	long storage = 1000000; //host storage
	int bw = 10000;

	hostList.add(
			new Host(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerSimple(bw),
				storage,
				peList1,
				new VmSchedulerTimeShared(peList1)
			)
		); // This is our first machine

	hostId++;
/*
	hostList.add(
			new Host(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerSimple(bw),
				storage,
				peList2,
				new VmSchedulerTimeShared(peList2)
			)
		); // Second machine
*/
	// 5. Create a DatacenterCharacteristics object that stores the
	//    properties of a data center: architecture, OS, list of
	//    Machines, allocation policy: time- or space-shared, time zone
	//    and its price (G$/Pe time unit).
	String arch = "x86";      // system architecture
	String os = "Linux";          // operating system
	String vmm = "Xen";
	double time_zone = 10.0;         // time zone this resource located
	double cost = 3.0;              // the cost of using processing in this resource
	double costPerMem = 0.05;		// the cost of using memory in this resource
	double costPerStorage = 0.1;	// the cost of using storage in this resource
	double costPerBw = 0.1;			// the cost of using bw in this resource
	LinkedList<Storage> storageList = new LinkedList<Storage>();	//we are not adding SAN devices by now

	DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
            arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);


	// 6. Finally, we need to create a PowerDatacenter object.
	Datacenter datacenter = null;
	try {
		datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
	} catch (Exception e) {
		e.printStackTrace();
	}

	return datacenter;
}



private static DatacenterBroker createBroker(String name){

	DatacenterBroker broker = null;
	try {
		broker = new DatacenterBroker(name);
	} catch (Exception e) {
		e.printStackTrace();
		return null;
	}
	return broker;
}

/**
 * Prints the Cloudlet objects
 * @param list  list of Cloudlets
 */
private static void printCloudletList(List<Cloudlet> list) {
	int size = list.size();
	Cloudlet cloudlet;
     double time_zone=10;
	String indent = "    ";
	Log.printLine();
	Log.printLine("========== OUTPUT ==========");
	Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
			"Data center ID" + indent + "VM ID" + indent + indent + "Time" + indent + "Start Time" + indent + "Finish Time");

	DecimalFormat dft = new DecimalFormat("###.##");
	for (int i = 0; i < size; i++) {
		cloudlet = list.get(i);
		Log.print(indent + cloudlet.getCloudletId() + indent + indent);

		if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS){
			Log.print("SUCCESS");

			Log.printLine( indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId() +
					indent + indent + indent + dft.format(cloudlet.getActualCPUTime()) +
					indent + indent + dft.format(cloudlet.getExecStartTime())+ indent + indent + indent + dft.format(cloudlet.getFinishTime())+ indent + indent + indent + cloudlet.getUtilizationOfCpu(CloudSim.clock())+ indent + indent + indent + dft.format(cloudlet.getUtilizationOfRam(time_zone)));
		}
	}

}

public static class GlobalBroker extends SimEntity {

	private static final int CREATE_BROKER = 0;
	private List<Vm> vmList;
	private List<Cloudlet> cloudletList;
	private DatacenterBroker broker;

	public GlobalBroker(String name) {
		super(name);
	}

	@Override
	public void processEvent(SimEvent ev) {
		switch (ev.getTag()) {
		case CREATE_BROKER:
			setBroker(createBroker(super.getName()+"_"));

			//Create VMs and Cloudlets and send them to broker
			setVmList(createVM(getBroker().getId(), 5, 100)); //creating 5 vms
			setCloudletList(createCloudlet(getBroker().getId(), 10, 100)); // creating 10 cloudlets

			broker.submitVmList(getVmList());
			broker.submitCloudletList(getCloudletList());

			CloudSim.resumeSimulation();

			break;

		default:
			Log.printLine(getName() + ": unknown event type");
			break;
		}
	}

	@Override
	public void startEntity() {
		Log.printLine(super.getName()+" is starting...");
		schedule(getId(), 200, CREATE_BROKER);
	}

	@Override
	public void shutdownEntity() {
	}

	public List<Vm> getVmList() {
		return vmList;
	}

	protected void setVmList(List<Vm> vmList) {
		this.vmList = vmList;
	}

	public List<Cloudlet> getCloudletList() {
		return cloudletList;
	}

	protected void setCloudletList(List<Cloudlet> cloudletList) {
		this.cloudletList = cloudletList;
	}

	public DatacenterBroker getBroker() {
		return broker;
	}

	protected void setBroker(DatacenterBroker broker) {
		this.broker = broker;
	}

}

}


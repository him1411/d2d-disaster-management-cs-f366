import java.util.*;

public class Driver
{
	public ArrayList<BaseStation> BSList;
	public ArrayList<DMS> DMSList;
	public ArrayList<UserEquipment> userEquipmentList;
	public int xLength=20, yLength=20, bsRange=6, ueRange = 2;
	public int numOfBaseStations=5, numOfUserEquipments=15;
	
	public void buildSimulationScenario()
	{
		BSList = new ArrayList<BaseStation>();
		DMSList = new ArrayList<DMS>();
		userEquipmentList = new ArrayList<UserEquipment>();
		
		int BSId = 100;
		for(int i=0;i<numOfBaseStations;i++)
		{
			BaseStation bs = new BaseStation(BSId+i, xLength, 0, yLength, 0);
			BSList.add(bs);
			DMSList.add(bs.createDMS());
		}
		
		int userId = 0;
		for(int i=0;i<numOfUserEquipments;i++)
		{
			UserEquipment ueq = new UserEquipment(userId+i, xLength, 0, yLength, 0, 100, true);
			userEquipmentList.add(ueq);
		}
		
		//Add user equipments to the simulation, need to add delays in connection, neglect handover
		for(UserEquipment ue : userEquipmentList)
		{
			double ueX = ue.locationX, ueY = ue.locationY;
			for(int i=0;i<numOfBaseStations;i++)
			{
				double bsX = BSList.get(i).locationX, bsY = BSList.get(i).locationY;
				double dist = Math.sqrt((ueX-bsX)*(ueX-bsX) + (ueY-bsY)*(ueY-bsY));
				if(dist<=bsRange)
				{
					BSList.get(i).connectedEquipmentsInfo.add(ue);
					DMSList.get(i).DMSDatabase.add(ue);
					break;
				}
			}
		}
		//print the simulation environment
		for(DMS dms : DMSList)
		{
			System.out.println("DMS Info " + dms.DMSId + " " + dms.locationX + " " + dms.locationY);
			for(UserEquipment ue : dms.DMSDatabase)
			{
				System.out.println("UE Info " + ue.userEquipmentId + " " + ue.locationX + " " + ue.locationY);
			}
			System.out.println("--------------------------------------------------------------------------------------------\n");
		}
	}
	
	public void transferInfoDMS(DMS d1, DMS d2)
	{																
		d2.DMSDatabase.addAll(d1.DMSDatabase);
	}
	
	public ArrayList<DMS> sendDMSSOS(DMS d1)
	{
		ArrayList<Integer> dmsIDList = new ArrayList<Integer>();
		ArrayList<DMS> dmsHelpers = new ArrayList<DMS>();
		
		double nearest = 0.0;
		for(int i=0;i<2;i++)
		{
			DMS d3 = new DMS();
			nearest = 1000.0;
			for(DMS d2 : DMSList)
			{
				if(d2.DMSId!=d1.DMSId && !dmsIDList.contains(d2.DMSId))
				{
					double dist = Math.sqrt((d1.locationX-d2.locationX)*(d1.locationX-d2.locationX)
									+ (d1.locationY-d2.locationY)*(d1.locationY-d2.locationY));
					if(dist<=nearest)
					{
						nearest = dist;
						d3 = d2;
					}
				}				
			}
			dmsIDList.add(d3.DMSId);
			dmsHelpers.add(d3);
		}
		
		for(DMS d : dmsHelpers)
		{
			System.out.println("DMS " + d.DMSId);
		}
		
		return dmsHelpers;
	}
	
	public void createD2DGraph(DMS d)
	{
		ArrayList<DMS> dmsHelpers = sendDMSSOS(d);
		for(DMS d1 : dmsHelpers)
		{
			transferInfoDMS(d, d1);
			HashMap<UserEquipment, ArrayList<UserEquipment>> adjList = new HashMap<UserEquipment, ArrayList<UserEquipment>>();
			
			//Create a mesh d2d network
			for(UserEquipment fromUE : d1.DMSDatabase)
			{
				adjList.put(fromUE, new ArrayList<UserEquipment>());
				for(UserEquipment toUE : d1.DMSDatabase)
				{
					if(fromUE.equals(toUE)) continue;
					
					double dist = Math.sqrt((fromUE.locationX-toUE.locationX)*(fromUE.locationX-toUE.locationX)
							+ (toUE.locationY-fromUE.locationY)*(toUE.locationY-fromUE.locationY));
					if(dist<=ueRange)
					{
						ArrayList<UserEquipment> temp = adjList.get(fromUE);
						temp.add(toUE);
						adjList.put(fromUE, new ArrayList<UserEquipment>(temp));
					}
				}
			}
			
			//Add helper base station(call it UE with ID 0) to the adjList
			UserEquipment helper = new UserEquipment(0, 0, 0, 0, 0, 100, true);
			helper.locationX = d1.locationX;
			helper.locationY = d1.locationY;
			adjList.put(helper, new ArrayList<UserEquipment>());
			for(UserEquipment toUE : d1.DMSDatabase)
			{
				double dist = Math.sqrt((helper.locationX-toUE.locationX)*(helper.locationX-toUE.locationX)
						+ (toUE.locationY-helper.locationY)*(toUE.locationY-helper.locationY));
				if(dist<=bsRange)
				{
					ArrayList<UserEquipment> temp1 = adjList.get(helper);
					temp1.add(toUE);
					adjList.put(helper, temp1);
				}
			}
			ArrayList<String> routeString = createRouteStrings(helper, adjList.size(), adjList, new ArrayList<>(d.DMSDatabase));
			
		}
	}
	
	public ArrayList<String> createRouteStrings(UserEquipment helper, int numOfNodes, HashMap<UserEquipment, 
												ArrayList<UserEquipment>> adjList, ArrayList<UserEquipment> sosUEList)
	{
		//Start from low power nodes, find route to helper base-station(UE), the route should have 
		//less number of middle nodes. Also, no low power device should be in the route.
		//Mark all visited nodes.
		Comparator<UserEquipment> UEComparator = new Comparator<UserEquipment>() {
			
			@Override
			public int compare(UserEquipment o1, UserEquipment o2) {
				// TODO Auto-generated method stub
				return (int)o1.batteryLevel-(int)o2.batteryLevel;
			}
		};
		
		ArrayList<String> routeStrings = new ArrayList<>();
		Collections.sort(sosUEList, UEComparator);
		
		for(UserEquipment ue : sosUEList)
		{	
			if(ue.isD2DRelay==true) continue;
			
			Queue<UserEquipment> q = new LinkedList<>();
			HashMap<UserEquipment, Boolean> visited = new HashMap<UserEquipment, Boolean>();
			HashMap<UserEquipment, UserEquipment> pred = new HashMap<UserEquipment, UserEquipment>();
			pred.put(ue, null);
			
			q.add(ue);
			visited.put(ue, true);
			int breakFlag = 0;
			
			while(!q.isEmpty())
			{
				UserEquipment fromNode = q.poll();
				for(UserEquipment toNode : adjList.get(fromNode))
				{
					if((visited.get(toNode)==null || visited.get(toNode)==false) && toNode.batteryLevel>40.0 && toNode.isD2DRelay==false)
					{
						visited.put(toNode, true);
						pred.put(toNode, fromNode);
						q.add(toNode);
						
						if(toNode.equals(helper))
						{
							breakFlag=1;
							break;
						}
					}
				}
				if(breakFlag==1) 
							break;
			}
			
			//Print the route string
			String route = new String("0");
			UserEquipment node = helper;
			while(pred.get(node)!=null)
			{
				route = route + "|" + pred.get(node).userEquipmentId;
				//mark d2d in sosList and adjList
				markD2D(pred.get(node), sosUEList, adjList, 1);
				node = pred.get(node);
			}
			routeStrings.add(route);
		}
				
		return routeStrings;
	}
	
	public boolean markD2D(UserEquipment u, ArrayList<UserEquipment> sosList, HashMap<UserEquipment, ArrayList<UserEquipment>> adjList, int mode)
	{
		boolean ret = false;
		for(UserEquipment ue : sosList)
		{
			if(u.userEquipmentId==ue.userEquipmentId) 
			{
				ret = ue.isD2DRelay;
				if(mode==1) ue.isD2DRelay=true;
			}
			
		}
		
		for(UserEquipment ueKey : adjList.keySet())
		{
			ArrayList<UserEquipment> ueList = adjList.get(ueKey);
			for(UserEquipment ue : ueList)
			{
				if(u.userEquipmentId==ue.userEquipmentId) 
				{
					ret = ue.isD2DRelay;
					if(mode==1) ue.isD2DRelay=true;
				}
				
			}
		}
		
		return ret;
		
	}
	
	//Create a mesh d2d network
	//Start from low power nodes, find route to helper base-station, the route should have 
	//less number of middle nodes. Also, no low power device should be in the route.
	//Mark all visited nodes.
	
	//Build the scenario placing BSs and UEs at random places DONE
	//D2D algorithm
	//Send DMS information to another DMS DONE
	//x,y UE points, random graph
	//Network delay
	//Time for d2d resolution
	//Battery discharge code
}


import java.util.*;

public class Node 
{
	//Node parameters
	private int ID; //Unique ID of the node
	private int numSlots; //Number of timeslots in a frame
	private double PoS; //Probability of message success
	private int range; //Radio range
	private int lane; //Lane
	private int xlanePosition; //X position
	private int ylanePosition; //Y position
	private int waitDelayFactor; //Number of frames to wait before contending for timeslot
	
	//Lists holding OHS and THS
	private List<Integer> OHS = new ArrayList<Integer>();
	private List<Integer> THS = new ArrayList<Integer>();
	
	//Referance to a global clock
	Clock clock = Clock.CLOCK;

	
	//Timeslot variables
	private int reservation = -1; // -1 is default if have no reservation
	private int originalReservation; //Backup reservation for when resetting the node
	private int originalReservationDuration; //Backup original duration when resetting node
	private ReservationBean[] timeSlots;
	private ReservationBean[] FI;
	
	//Original timeslot settings
	private ReservationBean[] originalTimeSlots;
	private ReservationBean[] originalFI;
	
	Random rng = new Random();
	
	//Retransmit parameters
	private boolean intention = false;
	private int retransIndex = -1;
	private int failIndex = -1;
	private int retransReceiver = -1;
	private int frameRetransmit = -1;
	private Message toCooperate = null;
	
	//Utilization statistics
	private int numSent = 0;
	private int success = 0;
	
	//Clock-collision parameters
	private int lastRecMsgTime = -1;
	private boolean decSuccess = true;
	private Message mayRemove = null; //Msg to remove from buffer if have a collision
	
	//Map of the messages received and the message in memory
	private Map<Integer,ArrayList<MemoryBean>> rMsgs = new HashMap<Integer,ArrayList<MemoryBean>>();
	
	//Retransmission Ack parameters
	private int ackSlot = -1;
	private int ackID = -1;
	
	//Message buffer to deal with collisions
	private List<Message> buffer = new ArrayList<Message>();
	
	//Parameters for reserving a slot
	private int waitCount = 0;
	private int maxReservationDuration;
	private int remainingReservationTime; //Remaining time until need to contend again
	
	//Jam parameters
	private boolean haveJam = false;
	
	List<Integer> allRec = new ArrayList<Integer>();
	
	RetransmitterTracker rt = RetransmitterTracker.RetransmittorTracker;
	
	public Node(int ID, int numSlots, double PoS, int range,int xlanePosition, int ylanePosition, int maxReservationDuration, int waitDelayFactor)
	{
		this.ID = ID; //No Change
		this.numSlots = numSlots; //No Change
		this.PoS = PoS; //No Change
		this.range = range; //No Change
		this.xlanePosition = xlanePosition; //No Change
		this.ylanePosition = ylanePosition; //No Change
		this.maxReservationDuration = maxReservationDuration; //No Change
		if(this.maxReservationDuration == Integer.MAX_VALUE)
		{
			this.originalReservation = Integer.MAX_VALUE;
		}else
		{
			this.originalReservationDuration = rng.nextInt(this.maxReservationDuration) + 1;
		}
		
		
		//******* Volatile, need to set back after a trial *****//
		
		
		//Sets timeslots and copy of timeslot values to default values
		this.remainingReservationTime = originalReservationDuration;
		timeSlots = new ReservationBean[this.numSlots];
		FI = new ReservationBean[this.numSlots];
		for(int i = 0; i < numSlots; i++)
		{
			timeSlots[i] = new ReservationBean(-1, this.maxReservationDuration); //Set all timeslots to -1
			FI[i] = new ReservationBean(-1, this.maxReservationDuration);
		}
		
		
		originalTimeSlots = new ReservationBean[this.numSlots];
		originalFI = new ReservationBean[this.numSlots];
		
		for(int i = 0; i < numSlots; i++)
		{
			originalTimeSlots[i] = new ReservationBean(-1, this.maxReservationDuration); //Set all timeslots to -1
			originalFI[i] = new ReservationBean(-1, this.maxReservationDuration);
			//System.out.println(originalTimeSlots[i].getHolder());
			//Scanner in = new Scanner(System.in);
			//in.nextLine();
		}
		this.waitDelayFactor = waitDelayFactor;
	}
 int getID() {
		return ID;
	}
	public void setID(int iD) {
		ID = iD;
	}
	public int getRemainingReservationDuration()
	{
		return this.remainingReservationTime;
	}
	public int getRange() {
		return range;
	}
	public int getLane() {
		return lane;
	}
	public int getXLanePosition() {
		return xlanePosition;
	}
	public int getyLanePosition() {
		return ylanePosition;
	}
	public int getNumSlots() {
		return numSlots;
	}
	public void setNumSlots(int numSlots) {
		this.numSlots = numSlots;
	}
	public double getPoS() {
		return PoS;
	}
	public void setPoS(double poS) {
		PoS = poS;
	}
	
	public void addToOHS(int addID)
	{
		this.OHS.add(addID);
	}
	public List<Integer> getOHS()
	{
		return this.OHS;
	}
	public void addToTHS(int addID)
	{
		this.THS.add(addID);
	}
	public List<Integer> getTHS()
	{
		return this.THS;
	}
	public int getReservation()
	{
		return this.reservation;
	}
	public void setReservation(int reservation)
	{
		this.originalReservation = reservation;
		this.reservation = reservation;
		this.timeSlots[reservation].setHolder(this.ID);
		this.timeSlots[reservation].setDuration(this.remainingReservationTime);
		
		
		this.FI[reservation].setHolder(this.ID);
		this.FI[reservation].setDuration(this.remainingReservationTime);
	}
	/**
	 * Only called during model initialization to initialize the FID values
	 */
	public void setFI(int index, int ID, int duration)
	{
		this.FI[index].setHolder(ID);
		this.FI[index].setDuration(duration);
	}
	/**
	 * Only called during model initialization to set reserved timeslots
	 * @param index
	 * @param ID
	 */
	public void setTimeSlotReservation(int index, int ID, int duration)
	{
		this.timeSlots[index].setHolder(ID);
		this.timeSlots[index].setDuration(duration);
	}
	
	public ReservationBean[] getTimeSlots()
	{
		return this.timeSlots;
	}
	public ReservationBean[] getFI()
	{
		return this.FI;
	}
	/**
	 * Sends an acknowledgement to a node which wishes to retransmit a message for the node
	 * The message contains the ID of the node which is permitted to send
	 * @return
	 */
	public ACKMessage sendACK()
	{
		int timeSlot = clock.getTimeSlot();
		if(timeSlot == this.ackSlot)
		{
//			System.out.println("Sending ack");
//			Scanner in = new Scanner(System.in);
//			in.nextLine();
			return new ACKMessage(ackID);
		}
		return null;
	}
	/**
	 * Recieves retransmit acknowledgement and proceeds tih retransmit or aborts
	 * @param m
	 */
	public void recACK(ACKMessage m)
	{
		if(this.rng.nextDouble() <= this.PoS)
		{
			if(this.ID != m.getAckID() && this.intention)
			{
				this.intention = false;
				this.failIndex = -1;
				this.retransIndex = -1;
				this.frameRetransmit = -1;
			}
		}
	}
	/**
	 * Returns true if the passed ID is in the nodes OHS, false otherwise
	 * @param ID
	 * @return
	 */
	public boolean recInOHS(int rID)
	{
		boolean inSet = false;
		if(this.OHS.contains(rID))
		{
			inSet = true;
		}else
		{
			inSet = false;
		}
		return inSet;
	}
	/**
	 * Returns true if the passed ID is in the nodes THS, false otherwise
	 * @param ID
	 * @return
	 */
	private boolean containedInTHS(int ID)
	{
		boolean contained = false;
		for(int i: this.THS)
		{
			if(i == ID)
			{
				contained = true;
				break;
			}
		}
		return contained;
	}
	private void clearSuccessFromCache(Message m)
	{
		ReservationBean[] mFI = m.getFID();
		ArrayList<MemoryBean> r = rMsgs.get(m.getSenderID());
		if(r != null)
		{
			for(int i = 0; i < r.size(); i++)
			{
				Message possibleRemove = r.get(i).getM();
				int pTimeSlot = possibleRemove.getTimeSlot();
				int pSender = possibleRemove.getSenderID();
				int pFrameMsgReceived = r.get(i).getFrameCreated();
				
				if(mFI[pTimeSlot].getHolder() == pSender && pFrameMsgReceived == (clock.getFrame() -1))
				{
					r.remove(i);
				}
			}
		}
	}

	/**
	 * removed the passed niehgbour ID from relationship tables
	 * @param ID
	 */
	public void removeNeighbour(int nID)
	{
		//this.OHS.remove(ID);
		for(int i = 0; i < OHS.size(); i++)
		{
			if(OHS.get(i) == nID)
			{
				OHS.remove(i);
				break;
			}
		}
		//this.THS.remove(ID);
		for(int i = 0; i < THS.size(); i++)
		{
			if(THS.get(i) == nID)
			{
				THS.remove(i);
				break;
			}
		}
		for(int i = 0; i < this.numSlots; i++)
		{
			if(this.timeSlots[i].getHolder() == nID)
			{
				this.timeSlots[i].setHolder(-1);
				this.FI[i].setHolder(-1);
			}
		}
	}
	/**
	 * Clears cached messages in memory greater than the passed value
	 */
	public void clearStaleCache(int maxStored)
	{
		int currentFrame = clock.getFrame();
		Set<Integer> keys = rMsgs.keySet();
		Iterator<Integer> it = keys.iterator();
		while(it.hasNext())
		{
			int key = (int) it.next();
			List<MemoryBean> memory = rMsgs.get(key);
			for(int i = 0; i < memory.size(); i++)
			{
				int memoryCreationFrame = memory.get(i).getFrameCreated();
				int diff = currentFrame - memoryCreationFrame;
				if(diff > maxStored)
				{
					memory.remove(i);
				}
			}
		}
	}
	/**
	 * Clears a nodes reservation and its OHS and THS
	 */
	public void clearReservation()
	{
	//	System.out.println("Node " + this.ID + " reservation cleared");
		this.reservation = -1;
		for(int i = 0; i < this.numSlots; i++)
		{
			this.timeSlots[i].setHolder(-1);
			this.FI[i].setHolder(-1);;
		}
		this.OHS = new ArrayList<Integer>();
		this.THS = new ArrayList<Integer>();
	}
	public int getNumSent()
	{
		return this.numSent;
	}
	public int getSuccess()
	{
		return this.success;
	}
	/*
	 * Sends a message (Returns a message to the model)
	 * 3 Possible scenarios:
	 * 
	 * 1. Node doesn't have a reserved timeslot - Tried to reserve a timeslot and returns null to model (No transmission)
	 * 2. Current timeslot matches the nodes reservation; node sends a message by sending returning a timeslot
	 * 3. Current timeslot matches the nodes intended retransmission timeslot - retransmit the message by returning the message
	 */
	public Message sendMsg()
	{
		Message toSend = null;
		if(this.reservation == -1)
		{
			attemptReservation();
			//System.out.println("No reservation " + this.ID);
		}
		
		if(this.reservation == clock.getTimeSlot() && this.OHS.size() > 0)
		{
			int msgID = Integer.parseInt(this.ID + "" + clock.getTime());
			int senderID = this.ID;
			int receiverID = this.OHS.get(rng.nextInt(this.OHS.size()));
			int timeSlot = clock.getTimeSlot();
			int creationTime = clock.getTime();
			boolean isRetransmit = false;
			ReservationBean[] Res = FI;
			CoopHeader c = new CoopHeader(intention, failIndex, retransIndex, retransReceiver);
			int remainingDuration = this.remainingReservationTime;
			toSend = new Message(msgID, senderID, receiverID, timeSlot, creationTime, isRetransmit, Res, c, remainingDuration);
			
			this.numSent++;
		}else if(this.retransIndex == clock.getTimeSlot() && this.frameRetransmit == clock.getFrame())
		{
			toSend = this.toCooperate;
			
			this.intention = false;
			this.failIndex = -1;
			this.retransIndex = -1;
			this.frameRetransmit = -1;
			this.retransReceiver = -1;
		}
		return toSend;
	}
	/*
	 * Adds a message to the received message buffer (Buffer simplifies detecting message collision without effecting model)
	 */
	public void addMessageToBuffer(Message m)
	{
		//System.out.println("Node ID " + this.ID + " Receiver ID: " + m.getReceiverID() + " Sender ID: " + m.getSenderID());
		this.buffer.add(m);
	}
	/*
	 * Processes the buffer
	 * 3 scenarios:
	 * 
	 * 1. Buffer only has 1 item - no message collision
	 * 2. Have a collision with a retransmission (Trigger JAM)
	 * 3. Have a collision with a contending node not aware of enviroment
	 */
	public void processBuffer()
	{
		int bufferSize = this.buffer.size();
		if(bufferSize == 1)
		{
			this.recieveMsg(buffer.get(0));
			//System.out.println("Buffer size 1");
		}else if(bufferSize > 1)
		{
			for(int i = 0; i < buffer.size(); i++)
			{
				//Scanner s = new Scanner(System.in);
			//	System.out.println("Collision: Node " + this.ID + " Message sender: " + buffer.get(i).getSenderID() + " Receiver id: " + buffer.get(i).getReceiverID());
				//s.nextLine();
			}
			
			int numNewMsg = 0;
			for(int i = 0; i < buffer.size(); i++)
			{
				if(!buffer.get(i).isRetransmit())
				{
					numNewMsg++;
				}
			}
			
			if(numNewMsg > 1)
			{
				this.haveJam = true;
			}
		}		
		
		buffer.clear();
	}
	/*
	 * Receives a message from another node once processed by the buffer
	 * 
	 */
	public void recieveMsg(Message m)
	{
		double didReceive = rng.nextDouble();
		/*******TEMPORARY**********/
		//didReceive = 0.0;
		/*******End Temp**********/
		int senderID = m.getSenderID();
		int receiverID = m.getReceiverID();
		boolean isRetransmit = m.isRetransmit();
		int senderRemainingDuration = m.getRemainingDuration();
		boolean recInOHS = this.recInOHS(receiverID);
		
		
		if(didReceive <= PoS) //Randomly determine if message is received based on PoS
		{
			if(isRetransmit == false)
			{
				if(recInOHS) //If the intended reciever is in the OHS of the node, add it to the retransmit tracker 
				{
					rt.AddPossibleRetransmitNode(m);
				}
				
				updateNeighbourTables(m); //Update neighbor tables based on the information in the message header
				
				if(receiverID == this.ID) //Intended recieve is current node (Receive the msg)
				{
					if(!allRec.contains(m.getMsgID()))
					{
						this.success++;
						this.allRec.add(m.getMsgID());
					}else
					{
						this.success--;
					}
					//System.out.println("Node " + this.ID + " got msg for it");
				}else if(recInOHS) //Intended receiver is not the current node but the intended receiver is in the current nodes OHS
				{
					//Add message to buffer
					if(rMsgs.containsKey(receiverID))
					{
						rMsgs.get(receiverID).add(new MemoryBean(m,clock.getFrame()));
					}else
					{
						ArrayList<MemoryBean> temp = new ArrayList<MemoryBean>();
						temp.add(new MemoryBean(m,clock.getFrame()));
						rMsgs.put(receiverID, temp);
					}
					
					//Check recieved message for Coop header in case need to transmit an C-ACK
					if(m.getCoHeader().getReceiverID() == this.ID)
					{
						this.ackID = senderID;
						this.ackSlot = m.getCoHeader().getIndexRetransmit();
					}
					//Check coop header for collisions in retransmission times (abort transmission if have a collision)
					if(this.checkForRetransCollision(m.getCoHeader()))
					{
						this.intention = false;
						this.failIndex = -1;
						this.retransIndex = -1;
						this.frameRetransmit = -1;
					}
					//Clear successful past msgs from cache based on the FI in the received message
					this.clearSuccessFromCache(m);
					
					//Check if cooperation triggers for the sending node
					boolean haveInBuffer =  haveNodeMessagesInBuffer(senderID);
					if(haveInBuffer && !this.intention && this.reservation != -1) //Must have a message for the node in the buffer and have a current reservation
					{
						//Search for a message to retransmit
						Message toRetransmit = findFailedMsg(m);
						if(toRetransmit != null) //Msg found
						{
							//Search for a free timeslot during which to attempt retransmission
							int retranSlot = this.findRetransmitSlot();
							if(retranSlot != -1) //Free slot found
							{
								//Set cooperation parameters
								this.intention = true;
								this.failIndex = toRetransmit.getTimeSlot();
								this.retransIndex = retranSlot;
								this.retransReceiver = toRetransmit.getReceiverID();
								
								toCooperate = toRetransmit;
								toCooperate.setRetransmit(true);
							//	System.out.println("Coop triggered");
								//Scanner input = new Scanner(System.in);
							//	input.nextLine();
							}
						}
					}
				}
			}else
			{
				if(m.getReceiverID() == this.ID) //Retransmit successfully received
				{
					success++;
				}
			}
		}else
		{
			this.FI[clock.getTimeSlot()].setHolder(-1); //Set FI to -1 indicating no msg during this timeslot - Possibly free or possible collision/Error in transmission
		}
	}
	/**
	 * Finds a slot in which to attempt retransmission
	 */
	public int findRetransmitSlot()
	{
		int slot = -1;
		for(int i = this.reservation; i < this.numSlots; i++)
		{
			if(this.timeSlots[i].getHolder() == -1)
			{
				slot = i;
				this.frameRetransmit = clock.getFrame() + 1;
				break;
			}
		}
		return slot;
	}
	/**
	 * Finds a failed msg for the sending node
	 */
	public Message findFailedMsg(Message m)
	{
		/*
		int mSender = m.getSenderID();
		ReservationBean[] mFI = m.getFID();
 		
		Message failed = null;
		
		ArrayList<MemoryBean> r = rMsgs.get(mSender);
		for(int i = 0; i < r.size(); i++)
		{
			Message p = r.get(i).getM();
			int pSlotReceived = p.getTimeSlot();
			if(this.timeSlots[pSlotReceived].getHolder() != -1 && mFI[pSlotReceived].getHolder() != this.timeSlots[i].getHolder() && mFI[i].getHolder() == -1)
			{
				r.remove(i);
				failed = p;
				break;
			}else
			{
				r.remove(i);
			}
		}
		
		return failed;
		*/
		Message failed = null;
		if(clock.getFrame() == 0)
		{
			return failed;
		}
		
		ArrayList<MemoryBean> cache = rMsgs.get(m.getSenderID());
		{
			for(int i = 0; i < cache.size(); i++)
			{
				Message old = cache.get(i).getM();
				ReservationBean[] mFI = m.getFID();
				int oldSendSlot = old.getTimeSlot();
				int oldSender = old.getSenderID();
				
				if(mFI[oldSendSlot].getHolder() == oldSender)
				{
					//System.out.println("REMOVING");
					cache.remove(i);
				}else if(mFI[oldSendSlot].getHolder() != oldSender && this.timeSlots[oldSendSlot].getHolder() != -1 
						/*&& this.timeSlots[oldSendSlot].getDuration() != 1*/)
				{
					failed = old;
					cache.remove(i);
					break;
				}
				
			}
		}
		
		return failed;
	}
	/**
	 * Checks if buffer contains any entry for this sender
	 * @param senderID
	 * @return
	 */
	public boolean haveNodeMessagesInBuffer(int senderID)
	{
		boolean recBefore = false;
		if(this.rMsgs.containsKey(senderID))
		{
			//System.out.println(clock.getTimeSlot());
			//Scanner in= new Scanner(System.in);
			//in.nextLine();
			recBefore = true;
		}
		return recBefore;
	}
	
	/**
	 * Check if have a collision during this slot to retransmit
	 * @param c
	 * @return
	 */
	public boolean checkForRetransCollision(CoopHeader c)
	{
		boolean haveCollision = false;
		if(c.getIntention() == true && this.intention == true)
		{
			if(this.retransIndex == c.getIndexRetransmit() || c.getIndexFailure() == this.failIndex)
			{
				haveCollision = true;
			}
		}
		return haveCollision;
	}
	public void updateNeighbourTables(Message m)
	{
		ReservationBean[] messageFI = m.getFID();
		
		this.timeSlots[clock.getTimeSlot()].setHolder(m.getSenderID());
		this.timeSlots[clock.getTimeSlot()].setDuration(m.getRemainingDuration());
		this.FI[clock.getTimeSlot()].setHolder(m.getSenderID());
		this.FI[clock.getTimeSlot()].setDuration(m.getRemainingDuration());
	
		
		if(!this.OHS.contains(m.getSenderID()))
		{
			this.OHS.add(m.getSenderID());
		}
		
		for(int i = 0; i < this.numSlots; i++)
		{
			if(this.timeSlots[i].getHolder() == -1 && messageFI[i].getHolder() != -1)
			{
//				Scanner in = new Scanner(System.in);
//				in.nextLine();
				this.timeSlots[i].setHolder(messageFI[i].getHolder());
				this.timeSlots[i].setDuration(messageFI[i].getDuration());
				//System.out.println("HERE");
			}
			if(!this.THS.contains(messageFI[i].getHolder()) && messageFI[i].getHolder() != -1)
			{
				this.THS.add(messageFI[i].getHolder());
			}
		}
		
		if(this.reservation == -1)
		{
			/***********************
			System.out.println("R: " + this.ID + " S: " + m.getSenderID());
			System.out.print("S: ");
			for(int i = 0; i < this.numSlots; i++)
			{
				System.out.print(messageFI[i].getHolder() + " , ");
			}
			System.out.println();
			System.out.print("R: ");
			for(int i = 0; i < this.numSlots; i++)
			{
				System.out.print(this.timeSlots[i].getHolder() + " , ");
			}
			System.out.println("\n\n");
			*************************/
//			
//			Scanner input = new Scanner(System.in);
//			input.nextLine();
		}
		/*************************
		System.out.println("R: " + this.ID + " S: " + m.getSenderID());
		System.out.print("S: ");
		for(int i = 0; i < this.numSlots; i++)
		{
			System.out.print(messageFI[i].getHolder() + " , ");
		}
		System.out.println();
		System.out.print("R: ");
		for(int i = 0; i < this.numSlots; i++)
		{
			System.out.print(this.timeSlots[i].getHolder() + " , ");
		}
		System.out.println("\n\n");
		*************************/
	}
	public void updateNodeDuration()
	{
		this.remainingReservationTime--;
		for(int i = 0; i < this.numSlots; i++)
		{
			this.timeSlots[i].decrementDuration();
			this.FI[i].decrementDuration();
		}
	}
	/*
	 * Clear expired reservations from the timeslots and FI fields based on remaining durations
	 */
	public void clearExpiredReservations()
	{
		//NEED TO IMPLEMENT
		for(int i = 0; i < this.numSlots; i++)
		{
			if(this.timeSlots[i].getHolder() != -1 && this.timeSlots[i].getDuration() <= 0)
			{
				rMsgs.remove(this.timeSlots[i].getHolder());
				this.timeSlots[i].setHolder(-1);
			}
			
			if(this.FI[i].getHolder() != -1 && this.FI[i].getDuration() <= 0)
			{
				this.FI[i].setHolder(-1);
			}
		}
		
		if(this.remainingReservationTime <= 0 && this.reservation >= 0)
		{
			//System.out.println("NODE "+ ID +" RESERVATION EXPIRED, OLD RESERVATION IS " + this.reservation);
			this.reservation = -1;
//			
//			Scanner in = new Scanner(System.in);
//			in.nextLine();
			
			for(int i = 0; i < numSlots; i++)
			{
				timeSlots[i] = new ReservationBean(-1, this.maxReservationDuration); //Set all timeslots to -1
				FI[i] = new ReservationBean(-1, this.maxReservationDuration);
			}
		}
	}
	/*
	 * Attempts to reserve a slot
	 * First ensures that the node has listened for at least 1 full frame
	 * Once the node has listened to at least 1 frame of timeslots attempts to find a free slot based on its view of timeslots
	 */
	public void attemptReservation()
	{
		if(waitCount < (this.numSlots * waitDelayFactor))
		{
			waitCount++;
		}else
		{
			List<Integer> possible = new ArrayList<Integer>();
			for(int i = 0; i < this.numSlots; i++)
			{
				if(timeSlots[i].getHolder() == -1)
				{
					possible.add(i);
				}
			}
			if(possible.size() > 0 && this.reservation == -1)
			{
				this.reservation = possible.get(rng.nextInt(possible.size()));
				this.remainingReservationTime = this.maxReservationDuration;
				this.waitCount = 0;
				
				this.timeSlots[reservation].setHolder(this.ID);
				this.timeSlots[reservation].setDuration(this.maxReservationDuration);
				
				this.FI[reservation].setHolder(this.ID);
				this.FI[reservation].setDuration(this.maxReservationDuration);
				
				//System.out.println("NODE " + ID + " RESERVING SLOT " + reservation);
//				Scanner input = new Scanner(System.in);
//				input.nextLine();
			}
		}
	}
	/*
	 * Sends a JAM message to the model indicating message collisions
	 */
	public JamMessage sendJam()
	{
		if(this.haveJam == true)
		{
			this.haveJam = false;
			//System.out.println("HAVE A JAM **********************************************");
			return new JamMessage(this.ID);
		}else
		{
			return null;
		}
	}
	/*
	 * Receives a JAM message and clears reservations of where JAM occured
	 */
	public void recJam(JamMessage j)
	{
		if(clock.getTimeSlot() == this.reservation)
		{
			//Have a JAM during my timeslot
		//	System.out.println("Node " + ID + " has JAM at timeslot " + clock.getTimeSlot() + " clearing and recontending");
			this.reservation = -1;
			this.remainingReservationTime = 0;
			this.clearExpiredReservations();
//			Scanner in = new Scanner(System.in);
//			in.nextLine();
		}else
		{
			this.FI[clock.getTimeSlot()].setHolder(-1);
			this.timeSlots[clock.getTimeSlot()].setHolder(-1);
		}
	}
	/*
	 * Resets a Node to its state at the start of the trial
	 */
	public void reset()
	{
		this.reservation = originalReservation;
		this.remainingReservationTime = originalReservationDuration;
		timeSlots = new ReservationBean[this.numSlots];
		FI = new ReservationBean[this.numSlots];
		for(int i = 0; i < numSlots; i++)
		{
			timeSlots[i] = new ReservationBean(originalTimeSlots[i].getHolder(), originalTimeSlots[i].getDuration()); //Set all timeslots to -1
			FI[i] = new ReservationBean(originalFI[i].getHolder(), originalFI[i].getDuration());
		}	
		
		this.numSent = 0;
		this.success = 0;
		
		this.intention = false;
		this.failIndex = -1;
		this.retransIndex = -1;
		this.frameRetransmit = -1;
		
		this.rMsgs.clear();
		this.retransReceiver = -1;
		
		this.waitCount = 0;
		
		this.ackSlot = -1;
		this.ackID = -1;
		
		//this.allRec.clear();
	}
	/*
	 * Saves timeslot and FI information for later retoration during reset. 
	 */
	public void saveValues()
	{
	//	System.out.println(this.FI[1].getHolder() + " " + this.FI[1].getDuration());
	//	System.out.println(this.timeSlots[1].getHolder() + " " + this.timeSlots[1].getDuration());
	//	System.out.println(this.originalFI);
		
		
		for(int i = 0; i < this.numSlots; i++)
		{
			originalTimeSlots[i] = new ReservationBean(timeSlots[i].getHolder(), timeSlots[i].getDuration()); //Set all timeslots to -1
			originalFI[i] = new ReservationBean(FI[i].getHolder(), FI[i].getDuration());
		}	
	}
	
}

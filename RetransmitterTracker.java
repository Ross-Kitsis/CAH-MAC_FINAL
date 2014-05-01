

import java.util.*;

/**
 * Tracks the number of nodes which may be able to retransmit a msg
 * @author Ross Kitsis
 *
 */
public class RetransmitterTracker 
{
	public final static RetransmitterTracker RetransmittorTracker = new RetransmitterTracker();
	private Map<Integer, Integer> rm = new HashMap<Integer, Integer>(); //Map tracking Message to # of possible retransmitters
	
	private RetransmitterTracker()
	{
		
	}
	/*
	 * Adds a message to the map or increments the number of retransmitters
	 */
	public void AddPossibleRetransmitNode(Message m)
	{
		if(rm.containsKey(m.getMsgID()))
		{
			//Have a mapping for the message already
			int oldVal = rm.get(m.getMsgID());
			int newVal = oldVal+1;
			rm.put(m.getMsgID(), newVal);
			//System.out.println("Updating message " + m.getMsgID() + " with value " + newVal);
		}else
		{
			//No mapping for this message yet
			rm.put(m.getMsgID(), 1);
		//	System.out.println("Adding message " + m.getMsgID() + " with value " + 1);
		}
	}
	public double getAverageNumberOfRetransmittors()
	{
		double avg = 0;
		int totalNumMsgs = rm.keySet().size();
		int totalNumRec = 0;
		
		Collection<Integer> allVal = rm.values();
		Iterator<Integer> it = allVal.iterator();
		
		while(it.hasNext())
		{
			//System.out.println(totalNumRec);
			totalNumRec += it.next();
		}
	//	System.out.println(totalNumRec);
		avg = (double)totalNumRec/(double)totalNumMsgs;
		
		//System.out.println("Total msgs: " + totalNumRec);
		//System.out.println("Total Received msgs: " + totalNumMsgs);
		
		return avg;
	}
	public void reset()
	{
		rm = new HashMap<Integer, Integer>();
	}
}

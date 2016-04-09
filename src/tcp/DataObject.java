package tcp;

public class DataObject
{

	private int method;
	private double bandwidth;
	// Only for Packet Pair
	private double sendBW, receiveBW;

	public DataObject(int method, double bandwidth)
	{
		this.method = method;
		this.bandwidth = bandwidth;
		this.sendBW = 0;
		this.receiveBW = 0;
	}

	public int getMethod()
	{
		return method;
	}

	public double getBandwidth()
	{
		return bandwidth;
	}

	public double getSendBW()
	{
		return sendBW;
	}

	public void setSendBW(double sendBW)
	{
		this.sendBW = sendBW;
	}

	public double getReceiveBW()
	{
		return receiveBW;
	}

	public void setReceiveBW(double receiveBW)
	{
		this.receiveBW = receiveBW;
	}

}

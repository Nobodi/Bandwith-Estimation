package tcp;

public class DataObject
{

	private int method;
	private int packetSize;
	private double bandwidth;

	public DataObject(int method, int packetSize, double bandwidth)
	{
		this.method = method;
		this.packetSize = packetSize;
		// Only 2 Values after comma
		this.bandwidth = Math.round(bandwidth * 100) / 100.0;
	}

	public int getMethod()
	{
		return method;
	}

	public double getBandwidth()
	{
		return bandwidth;
	}

	public int getPacketSize()
	{
		return packetSize;
	}

	public void setPacketSize(int packetSize)
	{
		this.packetSize = packetSize;
	}

}

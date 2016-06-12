package tcp;

import java.lang.reflect.Type;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

public class AddResultstoDatabase
{

	Connection con = null;
	PreparedStatement pst = null;

	String url = "jdbc:postgresql://127.0.0.1/bem";
	String user = "masuser";
	String password = "";

	public ArrayList<Results> getObjectsFromString(String input)
			throws JsonSyntaxException
	{
		Gson gson = new Gson();

		Type collectionType = new TypeToken<ArrayList<Results>>()
		{
		}.getType();

		return gson.fromJson(input, collectionType);
	}

	public Double[] getArrayFromList(ArrayList<Double> bandwidths)
	{
		Double[] arr = new Double[bandwidths.size()];
		for (int i = 0; i < bandwidths.size(); i++)
		{
			arr[i] = bandwidths.get(i);
		}

		return arr;
	}

	public void putDataintoDatabase(ArrayList<Results> results)
			throws SQLException
	{
		con = DriverManager.getConnection(url, user, password);

		for (int i = 0; i < results.size(); i++)
		{
			// Add Result-Object to database

			// Foreign Key
			int key = 0;
			String stm = "INSERT INTO results(wifi, mobile, provider, ccode, mcc, mnc, lac, cid) VALUES(?,?,?,?,?,?,?,?)";
			pst = con.prepareStatement(stm, new String[] { "result_id" });
			pst.setBoolean(1, results.get(i).isWifi());
			pst.setBoolean(2, results.get(i).isMobile());
			pst.setString(3, results.get(i).getProvider());
			pst.setString(4, results.get(i).getcCode());
			pst.setString(5, results.get(i).getMcc());
			pst.setString(6, results.get(i).getMnc());
			pst.setInt(7, results.get(i).getLac());
			pst.setInt(8, results.get(i).getCid());
			pst.executeUpdate();
			ResultSet rs = pst.getGeneratedKeys();
			if (rs.next())
			{
				key = rs.getInt(1);
				System.out.println("KEY: " + key);
			}

			// Add bandwidths to Database
			for (int k = 0; k < results.get(i).getObjects().size(); k++)
			{
				Bandwidths bw = results.get(i).getObjects().get(k);

				stm = "INSERT INTO bandwidths(method, bandwidth, result_id) VALUES(?,?,?)";
				pst = con.prepareStatement(stm);

				pst.setInt(1, bw.getMethod());
				Array inArray = con.createArrayOf("float4",
						this.getArrayFromList(bw.getBandwidths()));
				pst.setObject(2, inArray);
				pst.setInt(3, key);
				pst.executeUpdate();
			}

		}
	}

}

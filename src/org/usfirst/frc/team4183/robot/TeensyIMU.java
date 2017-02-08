package org.usfirst.frc.team4183.robot;


import java.io.PrintWriter;

import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;

public class TeensyIMU {
	
	private SerialPort serialPort;
	private double unwrappedYaw = 0.0;
	PrintWriter pw;


	public double getYawDeg() {
		return unwrappedYaw;
	}


	public TeensyIMU(){
		
		System.out.println("Starting Teensy");

		try {
			pw = new PrintWriter("imutest-"+System.currentTimeMillis()+".txt");
		} catch(Exception e) {
			e.printStackTrace();
		}

		try {
			
			String[] ports = SerialPortList.getPortNames();

			System.out.print("Ports:");
			for(String port : ports)
				System.out.print(" " + port);
			System.out.println();

			if( ports.length == 0) 
				throw new Exception("No ports available!");
			
			System.out.println("Using port:" + ports[0]);
			serialPort = new SerialPort(ports[0]);  // TODO how to choose if multiple ports?
			
			// Open serial port
			serialPort.openPort();
			// Set params
			serialPort.setParams(SerialPort.BAUDRATE_38400,   // FIXME NOT THIS! 115200! (and why?)
					SerialPort.DATABITS_8, 
					SerialPort.STOPBITS_1, 
					SerialPort.PARITY_NONE);
			
			// Start thread for reading in serial data
			new Thread(new TeensyRunnable()).start();
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}		
	}
	
	private float hexToDouble(String str){		
		//Parses string as decimal
		Long i = Long.parseLong(str, 16);
		//Converts newly created decimals to floating point    
		return Float.intBitsToFloat(i.intValue());
	}

	private long hexToLong(String str){
		return Long.parseLong(str,16);		
	}

	
	class TeensyRunnable implements Runnable {

		@Override
		public void run() {
			
			// Raw data
			String rawIn;
			
			// Buffer for raw data
			String inBuffer = "";
			
			while(true) {

				try {

					if( (rawIn = serialPort.readString()) == null) 
						continue;  // Nothing to read

					inBuffer += rawIn;  // Append new input
					
					// Process the input lines.
					// Lines ending is \r\n, but we have to watch out for 
					// fragment line at end of inBuffer.
					String[] lines = inBuffer.split("\n");
					inBuffer = "";
					for( int i = 0 ; i < lines.length ; i++) {						
						String line = lines[i];
						
						if( line.endsWith("\r")) {
							// Full line, chop off the \r
							line = line.substring(0, line.length()-1);							
							if( line.length() == IMUMESSAGELEN)
								processImuMsg(line);
						}
						else if( i == lines.length-1) {
							// Last "line" didn't end in \r, is actually a fragment,
							// put it back in buffer to be completed by further input					
							inBuffer = line;
						}						
					}
					
				} catch (SerialPortException e) {
					e.printStackTrace();
				}

				// Thread wait
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		private final int IMUMESSAGELEN = 39;
		private double prevYaw = Double.NaN;
		private double prevTime = Double.NaN;
		private double unwrapAccum = 0.0;
		private double prevWrappedYaw = Double.NaN;
		
		private void processImuMsg( String msg) {
						
			// poseData[]:
			// 0: IMU time, usecs: 8 hex chars representing 4-byte long
			// 1: Fusion status, boolean: 2 hex chars representing 1 byte boolean
			// 2: Roll, radians: 8 hex chars representing 4-byte IEEE 754 single-precision float
			// 3: Pitch, radians: same as Roll
			// 4: Yaw, radians: same as Roll
			String[] poseData = msg.split(",");
			if( poseData.length < 5)
				return;

			long imutime = hexToLong(poseData[0]);
			double wrappedYaw = hexToDouble(poseData[4])*(180.0/Math.PI);

			// Run the yaw angle unwrapper
			if( !Double.isNaN(prevWrappedYaw)) {
				if( wrappedYaw - prevWrappedYaw < -180.0 )
					unwrapAccum += 360.0;
				if( wrappedYaw - prevWrappedYaw > 180.0)
					unwrapAccum -= 360.0;
			}
			prevWrappedYaw = wrappedYaw;
			unwrappedYaw = wrappedYaw + unwrapAccum;
			
			// Calculate yaw rate (gotta watch out for excessive rate)
			if( !Double.isNaN(prevYaw)) {
				double timeDelta = (imutime - prevTime)/1000000.0;			
				double yawRate = (unwrappedYaw - prevYaw)/timeDelta;
				System.out.format("IMU Time:%d YawRate:%f Yaw:%f\n", imutime, yawRate, unwrappedYaw);			
//				pw.format("IMU Time:%d YawRate:%f Yaw:%f\n", imutime, yawRate, unwrappedYaw);
			}		
			prevTime = imutime;
			prevYaw = unwrappedYaw;
		}	
	}
}




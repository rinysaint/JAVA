/* 
 * 基于TCP协议的Socket通信，实现用户登陆响应,数据转发服务等服务器端
 * 开发者：xiaorining
 * 开发日期:2019年3月12日
 * 联系方式：QQ:164840753
 * 版本:v1.0
 */

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class tcpServer {    
	boolean started = false;
	ServerSocket serverSocket = null;
	static List<Client> clients = new ArrayList<Client>();
	
    //记录客户端的数量
	static int count=0;
    public static void main(String[] args) 
    {
    	new tcpServer().start();
	}    
  
    public void start() 
    {
        try 
        {        	
        	//1.创建一个服务器端Socket，即ServerSocket，指定绑定的端口，并监听此端口
        	serverSocket = new ServerSocket(4455, 10, InetAddress.getByName("192.168.1.243"));//192.168.1.243
        	started = true;
        	Socket socket=null;
        	timerclientsstatus();
	        msgshow("***服务器即将启动，等待客户端的连接***");

	        //循环监听等待客户端的连接
	        while(started)
		        {
	        	try 
		        	{
			            //调用accept()方法开始监听，等待客户端的连接
			            socket=serverSocket.accept();
			            //setKeepAlive＝true，若长时间没有连接则断开
						if(!socket.getKeepAlive()) {socket.setKeepAlive(true); }
			            Client c = new Client(socket);
			            new Thread(c).start();
			            clients.add(c);
			        }
	        	catch (BindException e) {
	        		msgshow("绑定IP和端口错误！");
	        		msgshow("请检查相关IP地址和端口并重新运行服务器！");
					serverSocket.close();
					started = false;
	    			System.exit(0);
	    		}
	        	catch(IOException  e) {e.printStackTrace();}
		        finally {
		            count=clients.size();//统计客户端的数量
		            msgshow("客户端的数量："+count);
		            InetAddress address=socket.getInetAddress();
		            msgshow("当前客户端的IP："+address.getHostAddress()+ ":"+ socket.getPort());
		            }
	        	}
        }
    	catch (BindException e) {
            msgshow("端口使用中....");
            msgshow("请关掉相关程序并重新运行服务器！"+e.getMessage());
			System.exit(0);
		}
        catch (IOException e) 
        {
        	e.printStackTrace();
        }
    }
        
	class Client implements Runnable 
	{
		private Socket soc;
		private DataInputStream dis = null;
		private DataOutputStream dos = null;
		private boolean bConnected = false;
		private InetAddress cip = null;
		private int cport = 0;
		private String code= "";

		public Client(Socket soc) 
		{
			this.soc = soc;
			try 
			{
				dis = new DataInputStream(soc.getInputStream());
				dos = new DataOutputStream(soc.getOutputStream());
				bConnected = (this.soc.getKeepAlive());
				cip = soc.getInetAddress();
				cport = soc.getPort();
				code = "";
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
		}
		
		public void send(byte[] str) 
		{
			try 
			{
				dos.write(str);
				dos.flush();
			} 
			catch (IOException e) 
			{
	            msgshow(cip+":"+ Integer.toString(cport) +" 退出了！从List里面去掉了！");
				clients.remove(this);
				count=clients.size();				
				bConnected = false;
			}
		}
		
		public void run() 
		{
			try 
			{
				if(this.soc.isConnected() && dis != null && dos != null) 
				{ 
				while (bConnected) 
				{			
					byte[] bfrec = new byte[1024];
					int rlen = dis.read(bfrec);
					if(rlen >-1) 
					{
						byte[] bfsum = new byte[rlen];
						System.arraycopy(bfrec,0, bfsum,0, rlen);
						//接收数据记录
						msgshow(" 接收到"+cip+":"+cport+":"+byte2HexStr(bfsum));
						//接收数据长度大于等于14且为##开头的命令才处理
						if(bfsum.length >= 14 && bfsum[0] == 0x23 && bfsum[1] ==0x23)
						{	
							int sumlength =0;		
							List<Integer> listlength = new ArrayList<Integer>();								

							for(int ilist=0;ilist<bfsum.length/14;ilist++)
							{
								try 
								{
									int sum=0,readsum=1;
									if(ilist > 0)
									{										
										sumlength += listlength.get(ilist-1);
									}
									int len = (bfsum[11+sumlength] & 0xFF);
									byte[] bf = new byte[len + 14];
									if(bfsum.length < sumlength+bf.length) {continue ;}
									System.arraycopy(bfsum,sumlength, bf,0, bf.length);									
									listlength.add(bf.length);
									if(bf.length >= 14 && bf[0] == 0x23 && bf[1] == 0x23)
									{
										readsum = ((bf[len+12] & 0xFF) <<8)+ (bf[len+13]& 0xFF);							
										for (int i=0;i<len+12;i++) {sum+= (bf[i] & 0xFF);}
									
										if(sum == readsum) //累加和校验
										{
											Thread.sleep(1);//防止命令转发耦合
											//发送端为站点处理方法
											if(bf[4] == 0x00)
											{
												String vno = Integer.toHexString(0xFF & bf[5])+Integer.toHexString(0xFF & bf[6])+Integer.toHexString(0xFF & bf[7])+Integer.toHexString(0xFF & bf[8])+Integer.toHexString(0xFF & bf[9])+Integer.toHexString(0xFF & bf[10]);						
												try
												{
													//登陆应答
													if(bf[2] == 0x00)
													{
														//更新站点路由表信息 
														updatecode(cip,cport,vno);
														bf[3] = (byte)Integer.parseInt("01",16);
														byte[] bfrc=CheckSum(bf);										
														this.send(bfrc);
														msgshow("站点登陆"+cip+":"+cport+":>>>"+byte2HexStr(bfrc));												
														continue;
													}
												}
												catch(Exception e)
												{
													msgshow(cip+":"+cport+" 站点登陆异常！"+e.getMessage());
													continue;
												}
												int flag=0;
												for(int i=0;i<clients.size();i++)
												{
													try 
													{
														if(clients.get(i).code.equals("01"))
														{
															clients.get(i).send(bf);
															flag =1;
															msgshow(cip+":"+cport+">>>" + clients.get(i).cip+":"+ clients.get(i).cport+": "+byte2HexStr(bf));
															
														}
														else
														{
															continue;
														}	
													}
													catch(Exception e)
													{
														msgshow(cip+":"+cport+">>>" + clients.get(i).cip+":"+ clients.get(i).cport+":"+" 信息发送异常！"+e.getMessage());
														continue;
													}
												}
												if(flag ==0)
												{
													try
													{
														bf[3]= (byte)Integer.parseInt("03",16);
														byte[] bfrc=CheckSum(bf);										
														this.send(bfrc);
														msgshow(cip+":"+cport+"信号丢失03:>>>"+byte2HexStr(bfrc));
													}
													catch(Exception e)
													{
														msgshow(cip+":"+cport+"信号丢失03:"+e.getMessage());
														continue;
													}
												}
											}
											//发送端为车辆处理方法
											if(bf[4] == 0x01)
											{
												String vno = Integer.toHexString(0xFF & bf[5])+Integer.toHexString(0xFF & bf[6])+Integer.toHexString(0xFF & bf[7])+Integer.toHexString(0xFF & bf[8])+Integer.toHexString(0xFF & bf[9])+Integer.toHexString(0xFF & bf[10]);
												try
												{
													//登陆应答
													if(bf[2]==0x00)
													{
														//更新车辆路由表信息
														updatecode(cip,cport,"01");
														bf[3] = (byte)Integer.parseInt("01",16);
														byte[] bfrc=CheckSum(bf);										
														this.send(bfrc);
														msgshow("车辆登陆"+cip+":"+cport+":>>>"+byte2HexStr(bfrc));
														continue;
													}
												}
												catch(Exception e)
												{
													msgshow(cip+":"+cport+" 站点登陆异常！"+e);
													continue;
												}
												int flag=0;
												for(int i=0;i<clients.size();i++)
												{
													try 
													{
														if(clients.get(i).code.equals(vno))
														{
															clients.get(i).send(bf);
															flag =1;
															msgshow(cip+":"+cport+">>>" + clients.get(i).cip+":"+ clients.get(i).cport+":"+byte2HexStr(bf));
															
														}
														else
														{
															continue;
														}	
													}
													catch(Exception e)
													{
														msgshow(cip+":"+cport+">>>" + clients.get(i).cip+":"+ clients.get(i).cport+":"+" 信息发送异常！"+e.getMessage());
														continue;
													}
												}
												if(flag ==0)
												{
													try
													{
														bf[3]= (byte)Integer.parseInt("03",16);
														byte[] bfrc=CheckSum(bf);										
														this.send(bfrc);
														msgshow(cip+":"+cport+"信号丢失03:>>>"+byte2HexStr(bfrc));
													}
													catch(Exception e)
													{
														msgshow(cip+":"+cport+"信号丢失03: "+e.getMessage());
														continue;
													}
												}
											}
										}
									}
								}
								catch(Exception e)
								{
									msgshow(cip+":"+cport+" 接收数据解析异常！"+e.getMessage());
									continue;
								}
							}						
//							else 
//							{
								//String str =new String(bf,0,rlen).toString().trim();
//								if(bf.length >0) 
//								{
//									msgshow("------------来自"+cip+":"+cport+":" + byte2HexStr(bf,bf.length));
	//									for (int i = 0; i < clients.size(); i++) 
	//									{
	//										Client c = clients.get(i);
	//										c.send(bf);
	//										//bConnected = false;
	//									}
//								}
//							}
						}
					}
					else
					{

						//Thread.sleep(10000);
//						byte[] bt= new byte[4];
//						bt = ("test").getBytes();
//						for (int i = 0; i < clients.size(); i++) 
//						{
//							Client c = clients.get(i);
//							c.send(bt);
//						}
						this.send(("test").getBytes());
						//bConnected = false;
					}
				}
			}
			}
			catch (EOFException e) 
			{
				msgshow("Client closed!"+e);
				clients.remove(this);
				count=clients.size();
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
				clients.remove(this);
				count=clients.size();
			} 
			catch (Exception e) 
			{
				msgshow("Client closed!"+e);
				clients.remove(this);
				count=clients.size();
			} 
//			finally {
//				try {
//					if (dis != null)
//					{	dis.close();}
//					if (dos != null)
//					{	dos.close();}
//					if (soc != null) 
//					{   soc.close();}
//				} catch (Exception e1) {
//					e1.printStackTrace();
//				}
//			}			
		}
		//更新路由信息
        private void updatecode(InetAddress cip,int cport,String val)
        {
        	for(int i=0;i<clients.size();i++) 
        	{
        		if(clients.get(i).code.equals(val)) 
        		{
        			if(clients.get(i).cip.equals(cip) && clients.get(i).cport ==cport)
            		{
            			
            		}
        			else
        			{
        				clients.get(i).code="";
        			}
        		}
    			if(clients.get(i).cip.equals(cip) && clients.get(i).cport ==cport)
        		{
        			clients.get(i).code = val;
        		}
        	}
        }
        //累加和校验
        private byte[] CheckSum(byte[] bf)
        {
            try
            {
            	int cks = 0;
                byte[] bfsum =  bf;
                int len = bfsum[11];
                for (int i = 0; i < len + 12; i++)
                {
                    cks += bfsum[i];
                }
                bfsum[len+12] =(byte)(cks >> 8);
                bfsum[len+13] = (byte)(cks & 0x00FF);
                byte[] returnMsg = new byte[len + 14];
                System.arraycopy(bfsum,0, returnMsg,0, len + 14);
                return returnMsg;
            }
            catch (Exception e)
            {
            	msgshow("校验异常：" + e.getMessage());
                byte[] a = new byte[1]; a[0] = (byte)Integer.parseInt("0", 16);
                return a;
            }
        }

		/**
	     * bytes转换成十六进制字符串
	     * @param b byte[] byte数组
	     * @param iLen int 取前N位处理 N=iLen
	     * @return String 每个Byte值之间空格分隔
	     */
	    private String byte2HexStr(byte[] byteArray) {
	        final StringBuilder hexString = new StringBuilder("");
	        if (byteArray == null || byteArray.length <= 0)
	            return null;
	        for (int i = 0; i < byteArray.length; i++) {
	            int v = byteArray[i] & 0xFF;
	            String hv = Integer.toHexString(v);
	            if (hv.length() < 2) {
	                hexString.append(0);
	            }
	            hexString.append(hv);
	        }
	        return hexString.toString().toLowerCase();
	    }

	}
	//心跳包
    public void timerclientsstatus() {  
        Runnable runnable = new Runnable() {  
            public void run() {  
		   		if(clients.size()>0) {
					for (int i = 0; i < clients.size(); i++) 
					{
						Client c = clients.get(i);
						try
						{	
//							c.soc.sendUrgentData(0);//发送1个字节的紧急数据，默认情况下，客户端没有开启紧急数据处理，不影响正常通信 
							c.send(("test").getBytes());
						}
						catch(Exception se)
						{
						   msgshow(c.cip+ Integer.toString(c.cport) +" 断线了！从List里面删掉了！");
						   count=clients.size();
						}
					}
				}
            }  
        };  
        ScheduledExecutorService service = Executors  
                .newSingleThreadScheduledExecutor();  
        // 第二个参数为首次执行的延时时间，第三个参数为定时执行的间隔时间  10分钟
        service.scheduleAtFixedRate(runnable, 1, 2, TimeUnit.MINUTES);  
    }
  
	
	//消息处理
	private static void msgshow(String msg)
	{
		//mf.addmsg(msg);
		System.out.println(msg);
		writeLog(msg);
	}
	
	/**
     * 
     * @param path
     * path:保存日志文件路径
     * @param content
     * content：日志内容
     */
	private static void writeLog(String content)
	{
		File writefile;
		try {
		// 通过这个对象来判断是否向文本文件中追加内容
		// boolean addStr = append;
			
		SimpleDateFormat formatterfile = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
		formatter.setTimeZone(TimeZone.getTimeZone("GMT+8"));
		Date currTime = new Date();
		String fileTime = new String(formatterfile.format(currTime));
		String thisTime = new String(formatter.format(currTime));
		
		String path = fileTime+"_logFile.txt";
		writefile = new File(path);

		// 如果文本文件不存在则创建它

		if (!writefile.exists()) {
			writefile.createNewFile();
			writefile = new File(path); // 重新实例化
			}
		

		FileOutputStream fw = new FileOutputStream(writefile,true);
		Writer out = new OutputStreamWriter(fw, "utf-8");
		out.write(thisTime+":"+content);
		String newline = System.getProperty("line.separator");
		//写入换行
		out.write(newline);
		out.close();
		fw.flush();
		fw.close();
		} 
		catch (Exception ex) 
		{
			System.out.println("日志写入异常：" + ex.getMessage());
		}
	}
}

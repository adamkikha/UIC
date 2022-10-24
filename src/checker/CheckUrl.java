package checker;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.JOptionPane;

import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;


public class CheckUrl implements Runnable {
	private String url , title = null;
	private Document doc;
	private static int depth , valid = 0 , invalid = 0;
	private static boolean isFirstRun;
	private static Thread main = Thread.currentThread();
    private static Checked table;
	private static ThreadGroup threads;
                
	public CheckUrl(String url) {
		this.url =url;
	}
	public CheckUrl(String url,String title) {
		this(url);
		this.title = title;
	}
	private CheckUrl connect() {
		try{
			doc = Jsoup.connect(url)
					.sslSocketFactory(socketFactory())
					.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.128 Safari/537.36 OPR/75.0.3969.259")
					.timeout(300000)
					.get();
            }
		catch (IOException ex) {
			doc = null;
		}
		catch (RuntimeException ex) {
			doc = null;
		}
		return this;
	}
        private CheckUrl connectFirst() {
            connect();
            if(table!=null){
            	if(doc!=null) {
	                if(title!=null && !title.isEmpty())
	                    table.write(url,title,"OK");
	                else
	                    table.write(url,doc.title(),"OK");
	                valid++;
            	}
            	else {
            		table.write(url, title, "INVALID");
            		invalid++;
            	}
            }
            return this;
	}
	@Override
	public void run() {
		connect();
		check(depth);
		if(main.getState().compareTo(Thread.State.TIMED_WAITING)==0)
			main.interrupt();
	}
	
	private void check(int depth) {
		if(depth < 1) return;
		if(this.doc == null) return;
		else {
			Elements links = doc.select("a[href~=^[^#].+]");
			if(!links.isEmpty()) {
				for (Element link : links) {
					if(!isFirstRun)
						new CheckUrl(link.attr("abs:href"),link.text()).connect().check(depth-1);
					else
						new CheckUrl(link.attr("abs:href"),link.text()).connectFirst().check(depth-1);
				} 
					
			}
			else return;
		}
	}
	public static void setDepth(int d) {
		depth = d;
	}
	
	private int sync(int noOfThreads) {
		Elements links = doc.select("a[href~=^[^#].+]");
		if(!links.isEmpty()) {
                    int size = links.size() , i=0;
                    if(noOfThreads==1){
                        for (;i<size;i++)
                            new CheckUrl(links.get(i).attr("abs:href"),links.get(i).text()).connectFirst().check(depth);
                    }
            else if(size > noOfThreads) {
				while(i<size) {
					while(threads.activeCount()<noOfThreads-1) {
						if(i<size) {
							new Thread(threads,new CheckUrl(links.get(i).attr("abs:href"),links.get(i).text())).start();
							i++;
						}
						else
							break;
					}
					if(i<size) {
						new CheckUrl(links.get(i).attr("abs:href"),links.get(i).text()).run();
						i++;
					}
				}
				synchronized (this) {
					while(threads.activeCount()>0) {
						try{
							Thread.sleep(1000);
							}
						catch(InterruptedException ex) {
						}
					}
				}
			} else return 0;
			return 1;
		} else return -1;
	}
	
	private double time(int noOfThreads) {
		long startingTime = System.currentTimeMillis();
		switch (sync(noOfThreads)) 
		{
			case 1:{
				double executionTime = (System.currentTimeMillis() - startingTime)/1000.0;
				System.out.printf("%nUsing %d Thread(s): %.2f s%n",noOfThreads,executionTime);
				return executionTime;
			}
			
			case 0:
				return 10000;
			
			case -1:{
                JOptionPane.showMessageDialog(null,"Couldn't find any links","no links present",0);
				double executionTime = (System.currentTimeMillis() - startingTime)/1000.0;
				return executionTime;
			} 
		
			default:
				return 0;
		}
			
	}
	public static boolean optimalRun(String input,Checked frame) {
		CheckUrl url = new CheckUrl(input).connect();
		if(url.doc == null){
                JOptionPane.showMessageDialog(null,"Please check the provided link","invalid link",0);
                return false;
                }
		else {
            threads = new ThreadGroup("checkers");
            CheckUrl.table = frame;
            isFirstRun = true;
            CheckUrl.valid = 0;
            CheckUrl.invalid = 0;
			double probableBest=url.time(1);
			int valid = CheckUrl.valid , invalid=CheckUrl.invalid;
			isFirstRun = false;
			double current=url.time(2);
			if (probableBest<0.1)
				return true;
			int noOfThreads = 2;
			while (noOfThreads<32) {
				if(current<probableBest) {
					probableBest=current;
					current=url.time(++noOfThreads);
				}
				else {
					current=url.time(++noOfThreads);
					if(current<probableBest)
						continue;
					else {
                                                table.setBestTimeField(Double.toString(probableBest));
                                                table.setNoOfThreadsField(noOfThreads-2);
                                                table.setvalidField(valid-1);
                                                table.setInvalidField(invalid);
						break;
					}
				}
			}
                        return true;
		}
	}

	private static SSLSocketFactory socketFactory() {
	    TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() 
	    	{
		        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
		            return new X509Certificate[0];
		        }
		
		        public void checkClientTrusted(X509Certificate[] certs, String authType) {
		        }
		
		        public void checkServerTrusted(X509Certificate[] certs, String authType) {
		        }
	    	}
	    };
	
	    try {
	        SSLContext sslContext = SSLContext.getInstance("SSL");
	        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
	        SSLSocketFactory result = sslContext.getSocketFactory();
	        return result;
	    } catch (NoSuchAlgorithmException | KeyManagementException e) {
	        throw new RuntimeException("Failed to create a SSL socket factory", e);
	    }
	}
        
}
package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.regex.Pattern;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * @author stevko
 *PA2_A helped a lot
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final int SERVER_PORT = 10000;
    private ContentResolver mContentResolver;
    private Uri mUri;
    private int msgKey = 0;
    private int lastProposal = -1;
    private int lastSeqNum = -1;
    private String myPort = "";
    /*Priority queue for the messages*/
    private PriorityQueue<Message> allMsgs = new PriorityQueue<Message>(4,new msgCompare());
    private HashMap<String,ArrayList<Double>> msgMap = new HashMap<String,ArrayList<Double>>();
    private ArrayList<Integer> allPorts = new ArrayList<Integer>(Arrays.asList(11108,11112,11116,11120,11124));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_group_messenger);
        mContentResolver = getContentResolver();
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger2.provider");
        uriBuilder.scheme("content");
        mUri = uriBuilder.build();



        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*Hack by Steve ko for creating AVD connection
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        Log.v("server",myPort);
        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            Log.v(TAG, "Next line with issue");
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {

            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * SEND BUTTON
         */

        final Button sendBtn = (Button)  findViewById(R.id.button4);

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText = (EditText) findViewById(R.id.editText1);
                Log.v("btn funtion","Button Works");
                String msg = editText.getText().toString();
                Log.v("send","message: "+msg);
                editText.setText("");

                /*Adding the value to message map
                 * Remeber to change it when you change the loop number above*/
                if(msgMap.containsKey(msg)){
                    Log.v("msgMap check",msg+" already exist");
                }
                msgMap.put(msg,new ArrayList<Double>());

                /*Calling client now*/
                Message message = new Message(msg, myPort, false, -1.0,"SENT");
                callClient(message);
            }
        });
    }
    /*@vjtg58 me came up with this hack*/
    public void callClient(Message message){

        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    /*Server Task is also AsyncTask*/
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            Log.v("doInBackgroung","VISITED");
            /*Send to onProgressUpdate().
             */
            ServerSocket serverSocket = sockets[0];
            Socket sS;
            String dead = " NOT DETECTED ";
            try {
                while(true){
                    Log.v("while loop","VISITED");
                    sS = serverSocket.accept();
                    dead =" DEAD PORT : "+sS.getPort();
                    Log.v("while loop","after the accept");
                    DataInputStream dis = new DataInputStream(sS.getInputStream());
                    String mread = dis.readUTF().trim();
                    dis.close();
                    sS.close();
                    Log.v("size of array",mread+" ");
                    String msg[] = mread.split(Pattern.quote("|"));
                    Log.v("size of array",msg[0]+" ");

                    if(msg[2].equals("PROPOSE")){
                        Log.v("location","Inside Propose condition");
                        int pval = Integer.parseInt(msg[1]);
                        pval = (pval - 11108)/2;
                        msgMap.get(msg[0]).add(Double.parseDouble(msg[3]));
                        ArrayList<Double> arr = msgMap.get(msg[0]);
                        Log.v("after propose","before the checkseq statement "+pval);
                        if(arr.size()>=allPorts.size()){
                            Log.v("after propose","Inside if statement checksequence");
                            for(int j = 0; j<arr.size();j++){
                                Log.v("propose num from "+msg[1]," "+arr.get(j));
                            }
                            Double seqNum = Collections.max(arr);
                            Log.v("value of seqNum"," "+seqNum);
                            Log.v("value of last seqNum"," "+lastSeqNum);
                            lastSeqNum = Math.max(lastSeqNum,Integer.valueOf((int)Math.floor(seqNum)));
                            for(int i = 0; i<allPorts.size(); i++){
                                try{
                                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), allPorts.get(i));
                                    //SocketAddress socAdd= new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), allPorts.get(i));
                                    //socket.connect(socAdd,10000);
                                    socket.setSoTimeout(2000);
                                    /*USED data-outpiut and input cause buffered had some issue*/
                                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                                    dos.writeUTF(msg[0]+"|"+msg[1]+"|"+"AGREED"+"|"+seqNum);
                                    dos.flush();
                                    dos.close();
                                    socket.close();
                                } catch(SocketTimeoutException ste){
                                    Log.e("propose","socket timeout exception");
                                } catch(SocketException se){
                                    Log.e("propose","socket timeout exception");
                                } catch (UnknownHostException e) {
                                    Log.e("propose", " UnknownHostException");
                                } catch (IOException e) {
                                    Log.e("propose", " socket IOException");
                                }catch(Exception e){
                                    Log.e("propose","Exception for "+i);
                                }

                            }
                        }
                    }
                    else if(msg[2].equals("AGREED")){
                        Log.v("location","Inside Agreed condition");
                        Double seqNum = Double.parseDouble(msg[3]);
                        Log.v("value of seqNum"," "+seqNum);
                        Log.v("value of last seqNum"," "+lastSeqNum);
                        lastSeqNum = Math.max(lastSeqNum,Integer.valueOf((int)Math.floor(seqNum)));
                        Log.v("updated lastSeqNum"," "+lastSeqNum);
                        msgMap.remove(msg[0]);
                        Message msgToRem = null;
                        for(Message m: allMsgs){
                            if(m.actMessage.equals(msg[0])){
                                msgToRem = m;
                                break;
                            }
                        }
                        boolean flag = allMsgs.remove(msgToRem);
                        Log.v("message removed",""+flag);
                        allMsgs.add(new Message(msg[0],msg[1],true,seqNum,msg[2]));
                        checkQueue();
                    }
                    else if(msg[2].equals("SENT")){
                        Log.v("location","Inside Sent condition");
                        try{
                            int portNum = Integer.parseInt(msg[1]);
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), portNum);
                            //SocketAddress socAdd= new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),portNum);
                            //socket.connect(socAdd,10000);
                            socket.setSoTimeout(2000);
                            /*Adding to the queue of messages*/
                            Log.v("while sending proposal","lastproposal "+lastProposal+" sequence "+lastSeqNum);
                            int proposeNum = Math.max(lastProposal,lastSeqNum) +1;
                            Double propDouble = Double.valueOf(proposeNum*1.0 + (Integer.parseInt(myPort) - 11108)/20.0);
                            lastProposal = proposeNum;
                            allMsgs.add(new Message(msg[0],msg[1],false,propDouble,"PROPOSE"));
                            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                            dos.writeUTF(msg[0]+"|"+myPort+"|"+"PROPOSE"+"|"+propDouble);
                            dos.flush();
                            dos.close();
                            socket.close();
                        } catch(SocketTimeoutException ste){
                            Log.e("sent","socket timeout exception");
                        } catch(SocketException se){
                            Log.e("sent","socket timeout exception");
                        } catch (UnknownHostException e) {
                            Log.e("sent", " UnknownHostException");
                        } catch (IOException e) {
                            Log.e("sent", " socket IOException");
                        }catch(Exception e){
                            Log.e("inside Sent","error in some port");
                        }

                    }

                }
            } catch(SocketTimeoutException ste){
                Log.e("server","socket timeout exception"+dead);
            } catch(SocketException se){
                Log.e("server","socket timeout exception"+dead);
            } catch (UnknownHostException e) {
                Log.e("server", "UnknownHostException"+dead);
            }catch (IOException e) {
                Log.e("server","IO exception error in accept"+dead);
            }
            //My code ends here
            return null;
        }

        public void checkQueue(){
            while(!allMsgs.isEmpty() && allMsgs.peek().getDelivery()){
                Message mx = allMsgs.poll();
                String strReceived = mx.getMsg();
                Log.v("removing from queue ",strReceived+" with seq num"+mx.getSequence());
                publishProgress(strReceived);
            }
        }

        protected void onProgressUpdate(String...strings) {
            /*The following code displays what is received in doInBackground().*/
            String strReceived = strings[0].trim();
            Log.v("progress","Our received message is "+strReceived+" <-should be here");
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\n");

            /*Code to save messages in content resolver or provider*/
            ContentValues cv = new ContentValues();
            cv.put("key",msgKey+"");
            msgKey++;
            cv.put("value",strReceived);
            mContentResolver.insert(mUri,cv);
            Log.v(TAG, "saving message went through");

            return;
        }
    }

    /*CLIENT Task is an AsyncTask to perform sending message */
    private class ClientTask extends AsyncTask<Message, Void, Void> {

        @Override
        protected Void doInBackground(Message... msgs) {
            String msgToSend =  msgs[0].getMsg();
            if(msgMap.containsKey(msgToSend)){
                Log.v("msg to send",msgs[0].getMsg());
                try {
                    for(int i = 0; i<allPorts.size(); i++){
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), allPorts.get(i));
                        //SocketAddress socAdd= new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), allPorts.get(i));
                        //socket.connect(socAdd,10000);
                        socket.setSoTimeout(2000);
                        /*USED data-outpiut and input cause buffered had some issue*/
                        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                        dos.writeUTF(msgToSend+"|"+myPort+"|"+"SENT");
                        dos.flush();
                        dos.close();
                        socket.close();
                    }
                } catch(SocketTimeoutException ste){
                    Log.e("server","socket timeout exception");
                } catch(SocketException se){
                    Log.e("propose","socket timeout exception");
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                }
            }

            return null;
        }
    }

    static class Message{
        public Double sequenceNum;
        public boolean delivery;
        public String sender;
        public String actMessage;
        public String type;
        public Message(String msg, String senderPort, boolean D, Double num, String msgType){
            sequenceNum = num;
            delivery = D;
            sender = senderPort;
            actMessage = msg;
            type = msgType;
        }
        public Double getSequence(){
            return sequenceNum;
        }
        public String getType(){
            return type;
        }
        public boolean getDelivery(){
            return delivery;
        }
        public String getMsg(){
            return actMessage;
        }
        public String getSender(){
            return sender;
        }
    }
    static class msgCompare implements Comparator<Message>{
        @Override
        public int compare(Message a, Message b){
            if(a.getSequence()>b.getSequence()){
                return 1;
            }
            else if(a.getSequence()<b.getSequence()){
                return -1;
            }
            else{
                return 0;
            }
        }
    }

    public void adjustQueue(int port){
        allPorts.remove(port);
        PriorityQueue<Message> tempQ = new PriorityQueue<Message>(allMsgs);
        for(Message m: tempQ){
            if(m.getSender().equals(""+port)){
                allMsgs.remove(m);
            }
        }
    }
}

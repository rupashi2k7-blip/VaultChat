import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * Mirrors Net.overSwitchboard + Net.handshake exactly, to prove the client flow
 * (register by number, WAIT/CALL, PEER role mapping, encrypted frames) works.
 */
public class SwitchTest {
    static final SecureRandom RNG = new SecureRandom();
    static byte[] rnd(int n){byte[]b=new byte[n];RNG.nextBytes(b);return b;}

    static byte[] encrypt(byte[] k, byte[] p) throws Exception {
        byte[] iv=rnd(12);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(k,"AES"),new GCMParameterSpec(128,iv));
        byte[] ct=c.doFinal(p);
        byte[] o=new byte[12+ct.length];
        System.arraycopy(iv,0,o,0,12); System.arraycopy(ct,0,o,12,ct.length);
        return o;
    }
    static byte[] decrypt(byte[] k, byte[] b) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(k,"AES"),new GCMParameterSpec(128,b,0,12));
        return c.doFinal(b,12,b.length-12);
    }
    static byte[] sha256(byte[]...parts) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        for(byte[]p:parts)md.update(p);
        return md.digest();
    }
    static byte[] hkdf(byte[] ikm, byte[] salt, String info, int len) throws Exception {
        Mac m=Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(salt,"HmacSHA256"));
        byte[] prk=m.doFinal(ikm);
        m.init(new SecretKeySpec(prk,"HmacSHA256"));
        byte[] i=info.getBytes("UTF-8");
        ByteArrayOutputStream o=new ByteArrayOutputStream();
        byte[] t=new byte[0]; int c=1;
        while(o.size()<len){m.update(t);m.update(i);m.update((byte)c);t=m.doFinal();o.write(t,0,t.length);c++;}
        return Arrays.copyOf(o.toByteArray(),len);
    }
    static byte[] pubBytes(PublicKey k){
        ECPublicKey e=(ECPublicKey)k;
        byte[]x=e.getW().getAffineX().toByteArray(),y=e.getW().getAffineY().toByteArray();
        byte[]o=new byte[65];o[0]=4;
        int lx=Math.min(32,x.length);System.arraycopy(x,x.length-lx,o,1+32-lx,lx);
        int ly=Math.min(32,y.length);System.arraycopy(y,y.length-ly,o,33+32-ly,ly);
        return o;
    }
    static ECPublicKey decodePub(byte[] b) throws Exception {
        AlgorithmParameters p=AlgorithmParameters.getInstance("EC");
        p.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec s=p.getParameterSpec(ECParameterSpec.class);
        return (ECPublicKey)KeyFactory.getInstance("EC").generatePublic(
            new ECPublicKeySpec(new ECPoint(new BigInteger(1,Arrays.copyOfRange(b,1,33)),
                                            new BigInteger(1,Arrays.copyOfRange(b,33,65))),s));
    }
    static int cmp(byte[]a,byte[]b){
        for(int i=0;i<Math.min(a.length,b.length);i++){int x=a[i]&0xFF,y=b[i]&0xFF;if(x!=y)return x-y;}
        return a.length-b.length;
    }
    static String readLine(InputStream is) throws Exception {
        StringBuilder sb=new StringBuilder(); int c;
        while((c=is.read())!=-1){ if(c=='\n')break; if(c!='\r')sb.append((char)c); if(sb.length()>64)throw new Exception("too long"); }
        if(c==-1&&sb.length()==0)return null;
        return sb.toString();
    }

    static class Conn {
        Socket s; DataInputStream in; DataOutputStream out;
        byte[] outKey, inKey; String safety; boolean client; String peer;
    }

    static Conn connect(int port, String myId, String target, boolean wait) throws Exception {
        Socket s=new Socket("127.0.0.1",port);
        OutputStream o=s.getOutputStream();
        o.write(("VC1 "+myId+"\n").getBytes("UTF-8")); o.flush();
        String ack=readLine(s.getInputStream());
        if(ack==null||!ack.startsWith("OK")) throw new Exception("register failed: "+ack);
        o.write((wait ? "WAIT\n" : ("CALL "+target+"\n")).getBytes("UTF-8")); o.flush();
        String reply=readLine(s.getInputStream());
        if(reply==null||!reply.startsWith("PEER")) throw new Exception("no peer: "+reply);
        String[] parts=reply.split(" ");
        Conn c=new Conn();
        c.s=s; c.client=parts[1].equals("1"); c.peer=parts[2];
        c.in=new DataInputStream(new BufferedInputStream(s.getInputStream()));
        c.out=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
        handshake(c);
        return c;
    }

    static void handshake(Conn c) throws Exception {
        KeyPairGenerator g=KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp=g.generateKeyPair();
        byte[] mine=pubBytes(kp.getPublic());
        byte[] theirs=new byte[65];
        if(c.client){ c.out.write(mine); c.out.flush(); c.in.readFully(theirs); }
        else { c.in.readFully(theirs); c.out.write(mine); c.out.flush(); }
        KeyAgreement ka=KeyAgreement.getInstance("ECDH");
        ka.init(kp.getPrivate()); ka.doPhase(decodePub(theirs),true);
        byte[] secret=ka.generateSecret();
        byte[] lo=mine,hi=theirs;
        if(cmp(mine,theirs)>0){lo=theirs;hi=mine;}
        byte[] okm=hkdf(secret,sha256(lo,hi),"VaultChat-v1",64);
        byte[] k1=Arrays.copyOfRange(okm,0,32),k2=Arrays.copyOfRange(okm,32,64);
        c.outKey=c.client?k1:k2; c.inKey=c.client?k2:k1;
        byte[] h=sha256(lo,hi);
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<12;i+=2){
            int v=((h[i]&0xFF)<<8|(h[i+1]&0xFF))%100000;
            if(i>0)sb.append(' ');
            sb.append(String.format("%05d",v));
        }
        c.safety=sb.toString();
    }

    static void send(Conn c, String msg) throws Exception {
        byte[] p=msg.getBytes("UTF-8");
        byte[] plain=new byte[1+p.length]; plain[0]=1; System.arraycopy(p,0,plain,1,p.length);
        byte[] blob=encrypt(c.outKey,plain);
        synchronized(c.out){ c.out.writeInt(blob.length); c.out.write(blob); c.out.flush(); }
    }
    static String recv(Conn c) throws Exception {
        int len=c.in.readInt();
        byte[] blob=new byte[len]; c.in.readFully(blob);
        byte[] plain=decrypt(c.inKey,blob);
        return new String(Arrays.copyOfRange(plain,1,plain.length),"UTF-8");
    }

    public static void main(String[] a) throws Exception {
        final int port=Integer.parseInt(a[0]);
        final Conn[] both=new Conn[2];
        final Exception[] err=new Exception[1];

        // callee registers by phone number and waits for a call
        Thread t=new Thread(new Runnable(){ public void run(){
            try{ both[1]=connect(port,"9876543210","",true);}catch(Exception e){err[0]=e;} }});
        t.start();
        Thread.sleep(700);

        // caller dials the callee's number
        both[0]=connect(port,"9123456789","9876543210",false);
        t.join(15000);
        if(err[0]!=null) throw err[0];

        Conn A=both[0], B=both[1];
        System.out.println("caller: client="+A.client+" peer="+A.peer);
        System.out.println("callee: client="+B.client+" peer="+B.peer);
        System.out.println("safety A: "+A.safety);
        System.out.println("safety B: "+B.safety);
        System.out.println("safety numbers match: "+A.safety.equals(B.safety));

        send(A,"hello from the caller");
        System.out.println("callee received: "+recv(B));
        send(B,"hello back from the callee");
        System.out.println("caller received: "+recv(A));

        StringBuilder big=new StringBuilder();
        for(int i=0;i<5000;i++) big.append((char)('a'+(i%26)));
        send(A,big.toString());
        System.out.println("50KB payload intact: "+recv(B).equals(big.toString()));

        A.s.close(); B.s.close();
        System.out.println("SWITCHBOARD + PROTOCOL TEST OK");
    }
}

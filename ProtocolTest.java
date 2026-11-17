import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class Test {
    static final SecureRandom RNG = new SecureRandom();
    static byte[] rnd(int n){byte[]b=new byte[n];RNG.nextBytes(b);return b;}
    static byte[] encrypt(byte[] k, byte[] p) throws Exception {
        byte[] iv=rnd(12);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(k,"AES"),new GCMParameterSpec(128,iv));
        byte[] ct=c.doFinal(p);
        byte[] o=new byte[12+ct.length];System.arraycopy(iv,0,o,0,12);System.arraycopy(ct,0,o,12,ct.length);return o;
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
        byte[] t=new byte[0];int c=1;
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
    static int cmp(byte[]a,byte[]b){for(int i=0;i<Math.min(a.length,b.length);i++){int x=a[i]&0xFF,y=b[i]&0xFF;if(x!=y)return x-y;}return a.length-b.length;}
    static String safety(byte[] mine, byte[] theirs) throws Exception {
        byte[]a=mine,b=theirs; if(cmp(mine,theirs)>0){a=theirs;b=mine;}
        byte[]h=sha256(a,b);
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<12;i+=2){int v=((h[i]&0xFF)<<8|(h[i+1]&0xFF))%100000;if(i>0)sb.append(' ');sb.append(String.format("%05d",v));}
        return sb.toString();
    }
    static byte[][] keys(KeyPair kp, byte[] theirs, boolean client) throws Exception {
        KeyAgreement ka=KeyAgreement.getInstance("ECDH");
        ka.init(kp.getPrivate()); ka.doPhase(decodePub(theirs),true);
        byte[] secret=ka.generateSecret();
        byte[] mine=pubBytes(kp.getPublic());
        byte[] lo=mine, hi=theirs; if(cmp(mine,theirs)>0){lo=theirs;hi=mine;}
        byte[] okm=hkdf(secret, sha256(lo,hi), "VaultChat-v1", 64);
        byte[]k1=Arrays.copyOfRange(okm,0,32),k2=Arrays.copyOfRange(okm,32,64);
        return client? new byte[][]{k1,k2} : new byte[][]{k2,k1};
    }
    public static void main(String[] a) throws Exception {
        KeyPairGenerator g=KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair A=g.generateKeyPair(), B=g.generateKeyPair();
        byte[] pa=pubBytes(A.getPublic()), pb=pubBytes(B.getPublic());
        System.out.println("pubkey length: "+pa.length+" / "+pb.length);
        byte[][] ka=keys(A,pb,true);   // client: out, in
        byte[][] kb=keys(B,pa,false);  // server: out, in
        boolean a2b = Arrays.equals(ka[0],kb[1]);
        boolean b2a = Arrays.equals(ka[1],kb[0]);
        System.out.println("client->server key match: "+a2b);
        System.out.println("server->client key match: "+b2a);
        System.out.println("keys differ per direction: "+!Arrays.equals(ka[0],ka[1]));
        String sa=safety(pa,pb), sb=safety(pb,pa);
        System.out.println("safety number: "+sa);
        System.out.println("safety numbers equal on both sides: "+sa.equals(sb));

        // full frame roundtrip through the client->server key
        String msg="namaste, yeh private hai";
        byte[] plain=msg.getBytes("UTF-8");
        byte[] frame=new byte[1+plain.length]; frame[0]=1; System.arraycopy(plain,0,frame,1,plain.length);
        byte[] blob=encrypt(ka[0],frame);
        byte[] back=decrypt(kb[1],blob);
        System.out.println("frame decrypt ok: "+(new String(Arrays.copyOfRange(back,1,back.length),"UTF-8").equals(msg))+" (type="+(back[0]&0xFF)+")");

        // tamper detection
        blob[blob.length-1]^=0x01;
        try{ decrypt(kb[1],blob); System.out.println("TAMPER NOT DETECTED  <-- BAD"); }
        catch(Exception e){ System.out.println("tampered frame rejected: "+e.getClass().getSimpleName()); }

        // PBKDF2 wrap/unwrap like the PIN path
        byte[] salt=rnd(16);
        byte[] kek=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(new PBEKeySpec("123456".toCharArray(),salt,150000,256)).getEncoded();
        byte[] mk=rnd(32);
        byte[] wrapped=encrypt(kek,mk);
        byte[] kek2=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(new PBEKeySpec("123456".toCharArray(),salt,150000,256)).getEncoded();
        System.out.println("PIN unwrap ok: "+Arrays.equals(mk,decrypt(kek2,wrapped)));
        byte[] wrong=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(new PBEKeySpec("123457".toCharArray(),salt,150000,256)).getEncoded();
        try{ decrypt(wrong,wrapped); System.out.println("WRONG PIN ACCEPTED <-- BAD"); }
        catch(Exception e){ System.out.println("wrong PIN rejected: "+e.getClass().getSimpleName()); }
    }
}

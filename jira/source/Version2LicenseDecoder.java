/*     */ package com.atlassian.extras.decoder.v2;
/*     */ 
/*     */ import com.atlassian.extras.common.LicenseException;
/*     */ import com.atlassian.extras.common.org.springframework.util.DefaultPropertiesPersister;
/*     */ import com.atlassian.extras.decoder.api.AbstractLicenseDecoder;
/*     */ import java.io.ByteArrayInputStream;
/*     */ import java.io.ByteArrayOutputStream;
/*     */ import java.io.DataInputStream;
/*     */ import java.io.DataOutputStream;
/*     */ import java.io.IOException;
/*     */ import java.io.InputStreamReader;
/*     */ import java.io.Reader;
/*     */ import java.io.UnsupportedEncodingException;
/*     */ import java.security.InvalidKeyException;
/*     */ import java.security.KeyFactory;
/*     */ import java.security.NoSuchAlgorithmException;
/*     */ import java.security.PublicKey;
/*     */ import java.security.Signature;
/*     */ import java.security.SignatureException;
/*     */ import java.security.spec.InvalidKeySpecException;
/*     */ import java.security.spec.X509EncodedKeySpec;
/*     */ import java.util.Properties;
/*     */ import java.util.zip.Inflater;
/*     */ import java.util.zip.InflaterInputStream;
/*     */ import org.apache.commons.codec.binary.Base64;
/*     */ 
/*     */ public class Version2LicenseDecoder extends AbstractLicenseDecoder
/*     */ {
/*     */   public static final int VERSION_NUMBER_1 = 1;
/*     */   public static final int VERSION_NUMBER_2 = 2;
/*     */   public static final int VERSION_LENGTH = 3;
/*     */   public static final int ENCODED_LICENSE_LENGTH_BASE = 31;
/*  55 */   public static final byte[] LICENSE_PREFIX = { 13, 14, 12, 10, 15 };
/*     */   public static final char SEPARATOR = 'X';
/*     */   private static final PublicKey PUBLIC_KEY;
/*     */   private static final int ENCODED_LICENSE_LINE_LENGTH = 76;
/*     */ 
/*     */   public boolean canDecode(String licenseString)
/*     */   {
/*  89 */     licenseString = removeWhiteSpaces(licenseString);
/*     */ 
/*  91 */     int pos = licenseString.lastIndexOf('X');
/*  92 */     if ((pos == -1) || (pos + 3 >= licenseString.length()))
/*     */     {
/*  94 */       return false;
/*     */     }
/*     */ 
/*     */     try
/*     */     {
/* 100 */       int version = Integer.parseInt(licenseString.substring(pos + 1, pos + 3));
/* 101 */       if ((version != 1) && (version != 2))
/*     */       {
/* 103 */         return false;
/*     */       }
/*     */ 
/* 106 */       String lengthStr = licenseString.substring(pos + 3);
/* 107 */       int encodedLicenseLength = Integer.valueOf(lengthStr, 31).intValue();
/*     */ 
/* 110 */       return pos == encodedLicenseLength;
/*     */     }
/*     */     catch (NumberFormatException e)
/*     */     {
/*     */     }
/*     */ 
/* 117 */     return false;
/*     */   }
/*     */ 
/*     */   public Properties doDecode(String licenseString)
/*     */   {
/* 123 */     String encodedLicenseTextAndHash = getLicenseContent(removeWhiteSpaces(licenseString));
/* 124 */     byte[] zippedLicenseBytes = checkAndGetLicenseText(encodedLicenseTextAndHash);
/* 125 */     Reader licenseText = unzipText(zippedLicenseBytes);
/*     */ 
/* 127 */     return loadLicenseConfiguration(licenseText);
/*     */   }
/*     */ 
/*     */   protected int getLicenseVersion()
/*     */   {
/* 132 */     return 2;
/*     */   }
/*     */ 
/*     */   private Reader unzipText(byte[] licenseText)
/*     */   {
/* 137 */     ByteArrayInputStream in = new ByteArrayInputStream(licenseText);
/* 138 */     in.skip(LICENSE_PREFIX.length);
/* 139 */     InflaterInputStream zipIn = new InflaterInputStream(in, new Inflater());
/*     */     try
/*     */     {
/* 142 */       return new InputStreamReader(zipIn, "UTF-8");
/*     */     }
/*     */     catch (UnsupportedEncodingException e)
/*     */     {
/* 147 */       throw new LicenseException(e);
/*     */     }
/*     */   }
/*     */ 
/*     */   private String getLicenseContent(String licenseString)
/*     */   {
/* 153 */     String lengthStr = licenseString.substring(licenseString.lastIndexOf('X') + 3);
/*     */     try
/*     */     {
/* 156 */       int encodedLicenseLength = Integer.valueOf(lengthStr, 31).intValue();
/* 157 */       return licenseString.substring(0, encodedLicenseLength);
/*     */     }
/*     */     catch (NumberFormatException e)
/*     */     {
/* 161 */       throw new LicenseException("Could NOT decode license length <" + lengthStr + ">", e);
/*     */     }
/*     */   }
/*     */ 
/*     */   private byte[] checkAndGetLicenseText(String licenseContent)
/*     */   {
/*     */     byte[] licenseText;
/*     */     try
/*     */     {
/* 170 */       byte[] decodedBytes = Base64.decodeBase64(licenseContent.getBytes());
/* 171 */       ByteArrayInputStream in = new ByteArrayInputStream(decodedBytes);
/* 172 */       DataInputStream dIn = new DataInputStream(in);
/* 173 */       int textLength = dIn.readInt();
/* 174 */       licenseText = new byte[textLength];
/* 175 */       dIn.read(licenseText);
/* 176 */       byte[] hash = new byte[dIn.available()];
/* 177 */       dIn.read(hash);
/*     */       try
/*     */       {
/* 181 */         Signature signature = Signature.getInstance("SHA1withDSA");
/* 182 */         signature.initVerify(PUBLIC_KEY);
/* 183 */         signature.update(licenseText);
/* 184 */         if (!signature.verify(hash))
/*     */         {
/* 186 */           throw new LicenseException("Failed to verify the license.");
/*     */         }
/*     */ 
/*     */       }
/*     */       catch (InvalidKeyException e)
/*     */       {
/* 192 */         throw new LicenseException(e);
/*     */       }
/*     */       catch (SignatureException e)
/*     */       {
/* 196 */         throw new LicenseException(e);
/*     */       }
/*     */       catch (NoSuchAlgorithmException e)
/*     */       {
/* 201 */         throw new LicenseException(e);
/*     */       }
/*     */ 
/*     */     }
/*     */     catch (IOException e)
/*     */     {
/* 207 */       throw new LicenseException(e);
/*     */     }
/*     */ 
/* 210 */     return licenseText;
/*     */   }
/*     */ 
/*     */   private Properties loadLicenseConfiguration(Reader text)
/*     */   {
/*     */     try
/*     */     {
/* 217 */       Properties props = new Properties();
/* 218 */       new DefaultPropertiesPersister().load(props, text);
/* 219 */       return props;
/*     */     }
/*     */     catch (IOException e)
/*     */     {
/* 223 */       throw new LicenseException("Could NOT load properties from reader", e);
/*     */     }
/*     */   }
/*     */ 
/*     */   private static String removeWhiteSpaces(String licenseData)
/*     */   {
/* 232 */     if ((licenseData == null) || (licenseData.length() == 0))
/*     */     {
/* 234 */       return licenseData;
/*     */     }
/*     */ 
/* 237 */     char[] chars = licenseData.toCharArray();
/* 238 */     StringBuffer buf = new StringBuffer(chars.length);
/* 239 */     for (int i = 0; i < chars.length; ++i)
/*     */     {
/* 241 */       if (Character.isWhitespace(chars[i]))
/*     */         continue;
/* 243 */       buf.append(chars[i]);
/*     */     }
/*     */ 
/* 247 */     return buf.toString();
/*     */   }
/*     */ 
/*     */   public static String packLicense(byte[] text, byte[] hash)
/*     */     throws LicenseException
/*     */   {
/*     */     try
/*     */     {
/* 259 */       ByteArrayOutputStream out = new ByteArrayOutputStream();
/* 260 */       DataOutputStream dOut = new DataOutputStream(out);
/* 261 */       dOut.writeInt(text.length);
/* 262 */       dOut.write(text);
/* 263 */       dOut.write(hash);
/*     */ 
/* 265 */       byte[] allData = out.toByteArray();
/* 266 */       String result = new String(Base64.encodeBase64(allData)).trim();
/*     */ 
/* 271 */       result = result + 'X' + "0" + 2 + Integer.toString(result.length(), 31);
/* 272 */       result = split(result);
/* 273 */       return result;
/*     */     }
/*     */     catch (IOException e)
/*     */     {
/* 278 */       throw new LicenseException(e);
/*     */     }
/*     */   }
/*     */ 
/*     */   private static String split(String licenseData)
/*     */   {
/* 287 */     if ((licenseData == null) || (licenseData.length() == 0))
/*     */     {
/* 289 */       return licenseData;
/*     */     }
/*     */ 
/* 292 */     char[] chars = licenseData.toCharArray();
/* 293 */     StringBuffer buf = new StringBuffer(chars.length + chars.length / 76);
/* 294 */     for (int i = 0; i < chars.length; ++i)
/*     */     {
/* 296 */       buf.append(chars[i]);
/* 297 */       if ((i <= 0) || (i % 76 != 0))
/*     */         continue;
/* 299 */       buf.append('\n');
/*     */     }
/*     */ 
/* 303 */     return buf.toString();
/*     */   }
/*     */ 
/*     */   static
/*     */   {
/*     */     try
/*     */     {
/*  65 */       String pubKeyEncoded = "MIIBuDCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYUAAoGBAIvfweZvmGo5otwawI3no7Udanxal3hX2haw962KL/nHQrnC4FG2PvUFf34OecSK1KtHDPQoSQ+DHrfdf6vKUJphw0Kn3gXm4LS8VK/LrY7on/wh2iUobS2XlhuIqEc5mLAUu9Hd+1qxsQkQ50d0lzKrnDqPsM0WA9htkdJJw2nS";
/*     */ 
/*  72 */       KeyFactory keyFactory = KeyFactory.getInstance("DSA");
/*  73 */       PUBLIC_KEY = keyFactory.generatePublic(new X509EncodedKeySpec(Base64.decodeBase64("MIIBuDCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYUAAoGBAIvfweZvmGo5otwawI3no7Udanxal3hX2haw962KL/nHQrnC4FG2PvUFf34OecSK1KtHDPQoSQ+DHrfdf6vKUJphw0Kn3gXm4LS8VK/LrY7on/wh2iUobS2XlhuIqEc5mLAUu9Hd+1qxsQkQ50d0lzKrnDqPsM0WA9htkdJJw2nS".getBytes())));
/*     */     }
/*     */     catch (NoSuchAlgorithmException e)
/*     */     {
/*  78 */       throw new Error(e);
/*     */     }
/*     */     catch (InvalidKeySpecException e)
/*     */     {
/*  83 */       throw new Error(e);
/*     */     }
/*     */   }
/*     */ }

/* Location:           D:\dev\java\decompile\jira\source\
 * Qualified Name:     com.atlassian.extras.decoder.v2.Version2LicenseDecoder
 * JD-Core Version:    0.5.4
 */
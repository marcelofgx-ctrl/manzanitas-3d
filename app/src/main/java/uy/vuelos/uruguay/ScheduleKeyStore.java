package uy.vuelos.uruguay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the private user's optional provider key encrypted with Android Keystore AES-GCM.
 * No API credential is present in repository, APK, logs or backup.
 */
public final class ScheduleKeyStore {
    private static final String KEY_ALIAS="uy_vuelos_schedule_api_key_v1";
    private static final String PREF="schedule_api_secure_v1";
    private static final String ENTRY="rapidapi_credential";
    private static final String TRANSFORM="AES/GCM/NoPadding";

    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry entry=store.getEntry(KEY_ALIAS,null);
        if(entry instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry)entry).getSecretKey();
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        KeyGenParameterSpec spec=new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)
            .build();
        gen.init(spec);
        return gen.generateKey();
    }

    public static void save(Context c,String token) throws Exception {
        if(token==null || token.trim().length()<8 || token.trim().length()>512)
            throw new IllegalArgumentException("Clave inválida");
        Cipher cipher=Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] plain=token.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] encrypted=cipher.doFinal(plain);
        byte[] iv=cipher.getIV();
        SharedPreferences.Editor edit=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit();
        edit.putString(ENTRY,Base64.encodeToString(iv,Base64.NO_WRAP)+":"+
            Base64.encodeToString(encrypted,Base64.NO_WRAP));
        if(!edit.commit())throw new IllegalStateException("No se pudo guardar la clave");
        java.util.Arrays.fill(plain,(byte)0);
    }

    public static String read(Context c) {
        try {
            String bundle=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(ENTRY,"");
            if(bundle.isEmpty())return "";
            String[] parts=bundle.split(":",2);
            if(parts.length!=2)return "";
            Cipher cipher=Cipher.getInstance(TRANSFORM);
            byte[] iv=Base64.decode(parts[0],Base64.NO_WRAP);
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            byte[] raw=cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP));
            String key=new String(raw,java.nio.charset.StandardCharsets.UTF_8);
            java.util.Arrays.fill(raw,(byte)0);
            return key;
        } catch(Exception ignored) {
            return "";
        }
    }
    public static boolean configured(Context c){return !read(c).isEmpty();}
    public static void clear(Context c){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(ENTRY).apply();
    }
    private ScheduleKeyStore(){}
}

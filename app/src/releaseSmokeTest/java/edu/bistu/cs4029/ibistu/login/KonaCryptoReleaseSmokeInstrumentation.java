package edu.bistu.cs4029.ibistu.login;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

/** Verifies that required KonaCrypto services survive release R8 processing. */
public final class KonaCryptoReleaseSmokeInstrumentation extends Instrumentation {

    private static final String[] REQUIRED_KONA_CRYPTO_CLASSES = {
        "com.tencent.kona.sun.security.ec.ECKeyFactory",
        "com.tencent.kona.crypto.provider.SM2Cipher",
        "com.tencent.kona.sun.security.util.ECParameters"
    };

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle results = new Bundle();
        try {
            ClassLoader classLoader = getTargetContext().getClassLoader();
            for (String className : REQUIRED_KONA_CRYPTO_CLASSES) {
                Class.forName(className, false, classLoader);
            }
            results.putString("stream", "\nKonaCrypto release smoke passed.\n");
            finish(Activity.RESULT_OK, results);
        } catch (Throwable error) {
            results.putString("shortMsg", error.toString());
            results.putString("stream", "\n" + Log.getStackTraceString(error) + "\n");
            finish(Activity.RESULT_CANCELED, results);
        }
    }
}

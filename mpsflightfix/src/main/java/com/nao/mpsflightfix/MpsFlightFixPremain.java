package com.nao.mpsflightfix;

import nilloader.api.ClassTransformer;
import nilloader.api.NilLogger;

/**
 * NilLoader premain entrypoint. Registers our single transformer.
 */
public class MpsFlightFixPremain implements Runnable {

    public static final NilLogger log = NilLogger.get("MpsFlightFix");

    @Override
    public void run() {
        log.info("Registering PlayerTickHandler bytecode patch");
        ClassTransformer.register(new PlayerTickHandlerTransformer());
    }
}

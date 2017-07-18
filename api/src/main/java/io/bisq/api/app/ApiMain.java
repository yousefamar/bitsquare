/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.api.app;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.bisq.common.UserThread;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.app.BisqExecutable;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static io.bisq.core.app.BisqEnvironment.DEFAULT_APP_NAME;
import static io.bisq.core.app.BisqEnvironment.DEFAULT_USER_DATA_DIR;


// TODO: check with seednodeMain because that's more up to date than the one I copied from, and fix in headless as well
public class ApiMain extends BisqExecutable {
    private static final Logger log = LoggerFactory.getLogger(ApiMain.class);
    private Api api;
    private boolean isStopped;

    public static void main(String[] args) throws Exception {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("HeadlessMain")
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));

        // We don't want to do the full argument parsing here as that might easily change in update versions
        // So we only handle the absolute minimum which is APP_NAME, APP_DATA_DIR_KEY and USER_DATA_DIR
        BisqEnvironment.setDefaultAppName("Bisq_api");
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        parser.accepts(AppOptionKeys.USER_DATA_DIR_KEY, description("User data directory", DEFAULT_USER_DATA_DIR))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.APP_NAME_KEY, description("Application name", DEFAULT_APP_NAME))
                .withRequiredArg();

        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException ex) {
            System.out.println("error: " + ex.getMessage());
            System.out.println();
            parser.printHelpOn(System.out);
            System.exit(EXIT_FAILURE);
            return;
        }
        BisqEnvironment BisqEnvironment = new BisqEnvironment(options);

        // need to call that before BitsquareAppMain().execute(args)
        BisqExecutable.initAppDir(BisqEnvironment.getProperty(AppOptionKeys.APP_DATA_DIR_KEY));

        // For some reason the JavaFX launch process results in us losing the thread context class loader: reset it.
        // In order to work around a bug in JavaFX 8u25 and below, you must include the following code as the first line of your realMain method:
        Thread.currentThread().setContextClassLoader(ApiMain.class.getClassLoader());
        if (options.has(AppOptionKeys.ENABLE_API) && options.valueOf(AppOptionKeys.ENABLE_API).equals(true)) {
            new ApiMain().execute(args);
        }
    }

    @Override
    protected void doExecute(OptionSet options) {
        Api.setEnvironment(new BisqEnvironment(options));
        UserThread.execute(() -> api = new Api());

        while (!isStopped) {
            try {
                Scanner scanner = new Scanner(System.in);
                while (scanner.hasNextLine()) {
                    String inputString = scanner.nextLine();
                    if (inputString.equals("q")) {
                        UserThread.execute(api::shutDown);
                        isStopped = true;
                    }
                }
            } catch (Throwable ignore) {
            }
        }
    }
}
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.foopara.phingking.exec;

import de.foopara.phingking.Helper;
import de.foopara.phingking.options.OptionMain;
import de.foopara.phingking.options.ProjectProperties;
import de.foopara.phingking.registry.TargetEntry;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputWriter;

/**
 *
 * @author n.specht
 */
public class RunTarget {
    private final Lookup lookup;
    private final TargetEntry target;

    public RunTarget(Lookup context, TargetEntry target) {
        this.lookup = context;
        this.target = target;
    }

    public void run() {
        try {
            if (!Helper.canRunPhing(this.lookup)) {
                return;
            }

            ProjectProperties pp = new ProjectProperties(this.lookup);
            StringBuilder cmd = new StringBuilder();

            File config = new File(pp.get("buildfile", null));
            File exe = new File(OptionMain.getExecutable());
            File logfile = File.createTempFile("phingking", ".log");

            cmd.append(exe.getAbsolutePath())
                    .append(" -logfile ")
                    .append(logfile.getAbsolutePath())
                    .append(" -f ")
                    .append(config.getAbsolutePath())
                    .append(" ");
            for(String paramKey : this.target.getParameter().keySet()) {
                cmd.append("-D")
                        .append(paramKey)
                        .append("=")
                        .append(this.target.getParameter().get(paramKey))
                        .append(" ");
            }
            cmd.append(this.target.getTarget());

            InputOutput ideIO = IOProvider.getDefault().getIO("PhingKing: " + this.target.getDisplayName(), true);
            ideIO.select();
            ideIO.setErrVisible(true);
            ideIO.setOutputVisible(true);
            ideIO.getOut().write("Running " + cmd.toString() + "\n\n");
            ideIO.getOut().flush();

            TailReader tr = new TailReader(ideIO, logfile);
            tr.start();

            Process child = Runtime.getRuntime().exec(cmd.toString(), null, config.getParentFile());
            StringBuilder tmp = new StringBuilder();
            InputStream in = child.getInputStream();
            int c;

            UserInput input = null;

            while((c = in.read()) != -1) {
                    tmp.append((char)c);
                    if (tmp.toString().trim().length() > 0) {
                        if (input == null || input.isDone()) {
                            input = new UserInput(child.getOutputStream());
                            input.setText(tmp.toString().trim());
                            input.start();
                        } else {
                            input.appendText(tmp.toString());
                        }
                        tmp = new StringBuilder();
                    }
            }

            try {
                if (child.waitFor() != 0) {
                    if (input != null && input.isDone() == false) {
                        input.setErrorMode();
                    } else {
                        input = new UserInput(child.getOutputStream());
                        input.setText(tmp.toString());
                        input.setErrorMode();
                        input.start();
                    }
                }
            } catch (InterruptedException ex) {

            }
            tr.stopReading();
            logfile.delete();

            ideIO.getOut().write("\n--- Phing has finished its job! ---\n\n");
            ideIO.getOut().flush();

            ideIO.getIn().close();
            ideIO.getOut().close();
            ideIO.getErr().close();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}

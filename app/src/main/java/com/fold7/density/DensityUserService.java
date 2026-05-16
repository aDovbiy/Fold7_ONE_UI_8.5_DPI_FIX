package com.fold7.density;

import android.content.Context;
import android.os.RemoteException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class DensityUserService extends IFoldDensityService.Stub {

    public DensityUserService() {
    }

    public DensityUserService(Context context) {
    }

    @Override
    public String applyFoldPreset(int density0, int density1) throws RemoteException {
        return runShell("wm density " + density0 + " -d 0 && wm density " + density1 + " -d 1");
    }

    @Override
    public String resetDensity() throws RemoteException {
        return runShell("wm density reset -d 0 && wm density reset -d 1");
    }

    @Override
    public void destroy() throws RemoteException {
        System.exit(0);
    }

    private String runShell(String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            int exitCode = process.waitFor();
            String stdout = readFully(process.getInputStream()).trim();
            String stderr = readFully(process.getErrorStream()).trim();

            StringBuilder result = new StringBuilder();
            result.append("$ ").append(command).append('\n');
            result.append("exitCode=").append(exitCode);
            if (!stdout.isEmpty()) {
                result.append("\nstdout:\n").append(stdout);
            }
            if (!stderr.isEmpty()) {
                result.append("\nstderr:\n").append(stderr);
            }
            return result.toString();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "Ошибка выполнения: " + e.getMessage();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}

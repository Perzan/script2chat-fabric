package me.perzan.fabric.s2c.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ServerEventHandler {

    static class ServerConfig {
        String script;
        boolean out = true;
        boolean err = false;
    }

    static class ScriptConfig {
        Map<String, ServerConfig> servers;
    }

    static final long SLEEP = 10;

    class Session extends Thread implements Closeable {

        final Process process;
        final ClientPlayerEntity player;
        final List<Scanner> scanners = new ArrayList<>();

        Session(ServerConfig config, ClientPlayerEntity player) throws IOException {
            this.process = Runtime.getRuntime().exec(config.script);
            this.player = player;

            if (config.out) scanners.add(new Scanner(process.getInputStream()));
            if (config.err) scanners.add(new Scanner(process.getErrorStream()));
        }

        @Override
        public void run() {
            while (!scanners.isEmpty()) {
                try {
                    Thread.sleep(SLEEP);
                    loop();
                } catch (InterruptedException e) {
                    return;
                }
            }

            player.sendMessage(Text.of("[Script2Chat] Process terminated with an exit code of " + process.exitValue()), false);
        }

        void loop() {
            for (Scanner scanner : new ArrayList<>(scanners)) {
                if (scanner.hasNextLine()) {
                    player.sendChatMessage(scanner.nextLine());
                } else {
                    scanners.remove(scanner);
                }
            }
        }

        @Override
        public void close() {
            this.interrupt();
            process.destroy();
        }
    }

    static final int BUFFER_SIZE = 8192;

    static long transfer(InputStream input, OutputStream output) throws IOException {
        Objects.requireNonNull(input, "arg0");
        Objects.requireNonNull(output, "arg1");

        long total = 0;
        int read;
        byte[] buffer = new byte[BUFFER_SIZE];

        while ((read = input.read(buffer)) > -1) {
            total += read;
            output.write(buffer, 0, read);
        }

        return total;
    }

    static InputStream resource() throws IOException {
        return MinecraftClient.getInstance().getResourceManager().getResource(new Identifier("script2chat", "script2chat.json")).getInputStream();
    }

    Session session;

    static final Gson GSON = new Gson();
    public void onGameJoin(GameJoinS2CPacket packet) {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("script2chat.json");
        if (!Files.exists(configPath)) {
            try {
                Files.createDirectories(configPath.getParent());
            } catch (IOException ioe) {
                ioe.printStackTrace();
                return;
            }

            try (InputStream resource = resource(); OutputStream output = Files.newOutputStream(configPath)) {
                transfer(resource, output);
            } catch (IOException ioe) {
                ioe.printStackTrace();
                return;
            }

            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        ServerInfo server = client.getCurrentServerEntry();
        
        if (server != null) {
            if (session != null)
                session.close();

            ScriptConfig config;
            try (Reader reader = new InputStreamReader(Files.newInputStream(configPath))) {
                config = GSON.fromJson(reader, ScriptConfig.class);
            } catch (IOException ioe) {
                ioe.printStackTrace();
                return;
            } catch (JsonParseException jpe) {
                client.player.sendMessage(Text.of("[Script2Chat] Failed to parse config"), false);
                client.player.sendMessage(Text.of(jpe.getMessage()), false);
                return;
            }

            if (config != null && config.servers != null) {
                ServerConfig serverConfig = config.servers.get(server.address.toLowerCase());

                if (serverConfig != null && serverConfig.script != null) {
                    try {
                        this.session = new Session(serverConfig, client.player);
                        session.start();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                        return;
                    }
                }
            }
        }
    }
    // public void onActivate(Function<Player, Function<String, Consumer<List<String>>>>) 

	public void onDisconnect(DisconnectS2CPacket packet) {
        if (session != null)
            session.close();
    }

    /*

    @Inject(at = @At("RETURN"), method = "remove")
    private void remove(ServerPlayerEntity player, CallbackInfo ci) {
        
    }

    */
}

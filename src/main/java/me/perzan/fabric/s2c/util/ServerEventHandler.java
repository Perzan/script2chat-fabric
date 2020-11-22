package me.perzan.fabric.s2c.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
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

public class ServerEventHandler {

    static class ServerConfig {
        String script;
        boolean out = true;
        boolean err = false;
    }

    static class ScriptConfig {
        Map<String, ServerConfig> servers;
    }

    static class Session implements Closeable {
        final Process process;
        final ClientPlayerEntity player;

        Session(ServerConfig config, ClientPlayerEntity player) throws IOException {
            this.process = Runtime.getRuntime().exec(config.script);
            this.player = player;

            if (config.out) new ScannerThread(process.getInputStream()).start();
            if (config.err) new ScannerThread(process.getErrorStream()).start();
        }

        class ScannerThread extends Thread {
            final Scanner scanner;

            ScannerThread(InputStream input) {
                Objects.requireNonNull(input, "arg0");
                this.scanner = new Scanner(input);
            }

            @Override
            public void run() {
                while (scanner.hasNextLine())
                    player.sendChatMessage(scanner.nextLine());
            }
        }

        @Override
        public void close() {
            process.destroy();
        }
    }

    Session session;

    void stop() {
        if (session == null) return;

        session.close();
        session = null;
    }

    static final Gson GSON = new Gson();
    public void onGameJoin(GameJoinS2CPacket packet) {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("script2chat.json");
        if (!Files.exists(configPath)) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        ServerInfo server = client.getCurrentServerEntry();
        
        if (server != null) {
            stop();

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
        stop();
    }
}

package com.ghatana.products.multi.agent.routing;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import io.activej.inject.annotation.Provides;
import io.activej.launcher.Launcher;
import io.activej.launchers.http.HttpServerLauncher;

public final class RoutingLauncher extends HttpServerLauncher {

    @Provides
    AsyncServlet servlet() {
        return request -> ResponseBuilder.ok().text("OK").build().toPromise();
    }

    public static void main(String[] args) throws Exception {
        Launcher launcher = new RoutingLauncher();
        launcher.launch(args);
    }
}

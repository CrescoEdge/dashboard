package io.cresco.dashboard.test;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Path("/dashboard/async")
public class Asyncpoll {

    private AsyncResponse asyncResponse;

    @Path("/poll")
    @GET
    public void poll(@Suspended final AsyncResponse asyncResponse)
            throws InterruptedException {
        asyncResponse.setTimeout(30, TimeUnit.SECONDS);
        this.asyncResponse = asyncResponse;
    }

    @POST
    @Path("/printed")
    public Response printCallback(String barcode) throws IOException {
        // ...

        this.asyncResponse.resume("MESSAGE");

        return Response.ok().build();
    }

}




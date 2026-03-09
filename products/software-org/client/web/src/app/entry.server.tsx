/**
 * Server Entry Point - React Router v7 Framework Mode
 *
 * This file handles server-side rendering for Framework Mode.
 * It's used during server-side rendering in production and development.
 *
 * @doc.type module
 * @doc.purpose Server-side entry point for Framework Mode
 * @doc.layer product
 * @doc.pattern Entry
 */
import { PassThrough } from "stream";
import { createReadableStreamFromReadable } from "@react-router/node";
import { renderToPipeableStream } from "react-dom/server";
import { ServerRouter } from "react-router";
import type { EntryContext } from "react-router";

export default function handleRequest(
    request: Request,
    responseStatusCode: number,
    responseHeaders: Headers,
    routerContext: EntryContext
) {
    return new Promise((resolve, reject) => {
        let shellRendered = false;

        const { pipe, abort } = renderToPipeableStream(
            <ServerRouter context={routerContext} url={request.url} />,
            {
                onShellReady() {
                    shellRendered = true;
                    const body = new PassThrough();
                    const stream = createReadableStreamFromReadable(body);

                    responseHeaders.set("Content-Type", "text/html");

                    resolve(
                        new Response(stream, {
                            status: responseStatusCode,
                            headers: responseHeaders,
                        })
                    );

                    pipe(body);
                },
                onShellError(error: unknown) {
                    reject(error);
                },
                onError(error: unknown) {
                    responseStatusCode = 500;
                    if (shellRendered) {
                        console.error(error);
                    }
                },
            }
        );

        setTimeout(() => abort(), 5000);
    });
}

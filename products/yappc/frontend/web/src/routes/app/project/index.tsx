import { Navigate } from "react-router";

/**
 * Project index route - redirects to canvas tab
 */
export default function ProjectIndexRoute() {
    return <Navigate to="canvas" replace />;
}

/**
 * Tests for Polyglot Fixture Rust Service
 * 
 * @doc.type module
 * @doc.purpose Tests for Rust service surface
 * @doc.layer product
 * @doc.pattern Test
 */

#[cfg(test)]
mod tests {
    use super::*;
    use actix_web::{test, web, App};

    #[actix_web::test]
    async fn test_health_endpoint() {
        let app = test::init_service(
            App::new().route("/health", web::get().to(health))
        ).await;

        let req = test::TestRequest::get().uri("/health").to_request();
        let resp = test::call_service(&app, req).await;

        assert!(resp.status().is_success());
    }

    #[actix_web::test]
    async fn test_ping_endpoint() {
        let app = test::init_service(
            App::new().route("/api/ping", web::get().to(ping))
        ).await;

        let req = test::TestRequest::get().uri("/api/ping").to_request();
        let resp = test::call_service(&app, req).await;

        assert!(resp.status().is_success());
    }
}

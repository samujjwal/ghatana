# Build stage
FROM golang:1.25-alpine AS builder

RUN apk add --no-cache git make

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .

# Build with OPA tag
RUN CGO_ENABLED=0 GOOS=linux go build -tags=opa -o /app/server ./cmd/server

# Final stage
FROM alpine:3.19
RUN apk add --no-cache ca-certificates
WORKDIR /app
COPY --from=builder /app/server /app/server
EXPOSE 8080 50051
CMD ["/app/server"]


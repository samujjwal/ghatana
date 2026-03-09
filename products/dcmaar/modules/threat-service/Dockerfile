# Build stage
FROM golang:1.25-alpine AS builder

# Install build dependencies
RUN apk add --no-cache \
    git \
    protobuf \
    protobuf-dev \
    make

# Set working directory
WORKDIR /app

# Copy go mod files first for better caching
COPY go.mod go.sum ./

# Download dependencies
RUN go mod download

# Install protoc plugins
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.34.1 && \
    go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@v1.3.0

# Copy the rest of the application
COPY . .

# Copy proto files and generate code
RUN mkdir -p /proto && \
    cp -r ../../core/proto/* /proto/ && \
    mkdir -p /proto/gen/go && \
    cd /proto && \
    PATH=$PATH:$(go env GOPATH)/bin /app/scripts/generate_protos.sh

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -o /app/server ./cmd/server

# Final stage
FROM alpine:3.19

# Install runtime dependencies
RUN apk add --no-cache ca-certificates

# Set working directory
WORKDIR /app

# Copy binary from builder
COPY --from=builder /app/server /app/server

# Copy generated proto files
COPY --from=builder /proto/gen /proto/gen

# Set environment variables
ENV GIN_MODE=release
ENV PORT=8080

# Expose port
EXPOSE $PORT

# Run the application
CMD ["/app/server"]

module github.com/samujjwal/dcmaar/scripts/loadtest

go 1.25.1

require (
	github.com/samujjwal/dcmaar/proto/gen/go/pb v0.0.0
	google.golang.org/grpc v1.75.1
	google.golang.org/protobuf v1.36.9
)

require (
	github.com/envoyproxy/protoc-gen-validate v1.2.1 // indirect
	golang.org/x/net v0.41.0 // indirect
	golang.org/x/sys v0.33.0 // indirect
	golang.org/x/text v0.26.0 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20250707201910-8d1bb00bc6a7 // indirect
)

replace github.com/samujjwal/dcmaar/proto/gen/go/pb => ../../core/proto/gen/go/pb

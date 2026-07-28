.PHONY: build clean

build:
	GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -tags lambda.norpc -o bootstrap .
	zip function.zip bootstrap

clean:
	rm -f bootstrap function.zip

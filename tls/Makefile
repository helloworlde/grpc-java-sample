cert:
	cd src/main/resources/cert && \
	openssl genrsa -out ca.key 4096 && \
	openssl req -new -x509 -key ca.key -sha256 -subj "/C=US/ST=NJ/O=CA, Inc." -days 3650 -out ca.cert && \
	openssl genrsa -out private.key 4096 && \
	openssl req -new -key private.key -out private.csr -config certificate.conf && \
	openssl x509 -req -in private.csr -CA ca.cert -CAkey ca.key -CAcreateserial -out server.pem -days 3650 -sha256 -extfile certificate.conf -extensions req_ext && \
	openssl pkcs8 -topk8 -nocrypt -in private.key -out server.key

clean:
	cd src/main/resources/cert && \
	rm -rf ca.* && \
	rm -rf private.* && \
	rm -rf server.*
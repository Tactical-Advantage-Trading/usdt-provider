# USDt provider
USDt chain data provider for Key wallet.

## Installation and config on server and in wallet:

1. Install and configure PostgreSQL on a server.
2. Generate a self-signed `keystore.p12` certificate, add it to `/src/main/resources`.
3. Extract public key and copy it to wallet folder (cert pinning):
```
$ openssl pkcs12 -in keystore.p12 -clcerts -nokeys -out ta_leaf.pem
$ openssl x509 -in ta_leaf.pem -outform DER -out ta_leaf.cer
$ mv ta_leaf.cer <Key wallet>/app/src/main/res/raw/ta_leaf.cer
```
4. Add the following `application.conf` to `/src/main/resources/`:
```
usdt {
    relationalDb = {
        connectionPool = "HikariCP"
        dataSourceClass = "org.postgresql.ds.PGSimpleDataSource"
        numThreads = 2

        properties = {
            user = "postgres"
            password = "<PostgreSQL password>"
            serverName = "<Server IP address>"
            databaseName = "usdt"
            portNumber = "5432"
        }
    }

    usdtDataProvider = {
        // USDt contract on Polygon mainnet
        contract = "0xc2132D05D31c914a87C6611C10748AEb04B58e8F"
        // https://chainlist.org/chain/137
        http1 = "https://polygon-bor.publicnode.com"
        http2 = "https://polygon.gateway.tenderly.co"
        http3 = "https://polygon.drpc.org"
        wss1 = "wss://polygon-bor-rpc.publicnode.com"
        wss2 = "wss://polygon.gateway.tenderly.co"
        wss3 = "wss://polygon.drpc.org"
    }

    websocketServerPort = 8080
}
```
5. Compile and run a fat JAR:
```
$ cd usdt-provider
$ sbt clean assembly
$ java -Dconfig.file=application.conf -jar usdt-assembly-0.1.0-SNAPSHOT.jar
```
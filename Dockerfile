# Imagen base ligera de Java 17 basada en entorno Ubuntu
FROM eclipse-temurin:17-jdk-jammy

# Directorio de trabajo dentro del contenedor
WORKDIR /app



CMD chmod +x iniciar.sh && date && ./iniciar.sh
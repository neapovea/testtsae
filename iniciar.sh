#!/bin/bash

echo "-*---------------------------------------Probando FASE." $OPCION_FASE
# 1. Comprobar si se ha pasado al menos un parámetro
if [ -z "$OPCION_FASE" ]; then
    echo "Error: No se ha proporcionado ningún parámetro."
    echo "Uso: $0 <2|3|4>"
    exit 1
fi


echo "-*---------------------------------------Compilar."
# COMPILAR
find src -name "*.java" > sources.txt
javac -version
javac --release 17 -d bin -cp "lib/*" @sources.txt


# MOSTRAR RESULTAdos
echo "-*---------------------------------------Comprobar compilación."
date
echo "-*---------------------------------------Probando FASE." $OPCION_FASE
ls  -lh bin/recipes_service/ServerData.class bin/recipes_service/tsae/data_structures/* bin/recipes_service/tsae/sessions/*  scripts/Results*; cat scripts/Results 



# 2. Guardar el primer parámetro en una variable
CANTIDAD_NODOS=15


cd scripts
chmod +x  start.sh 

# 3. Evaluar el parámetro y lanzar el comando correspondiente
case "$OPCION_FASE" in
    2)
        echo "Opción 2 recibida. Lanzando comando para el entorno 2..."
        # Sustituye la siguiente línea por tu comando real (ej: compilación, ejecución Java, etc.)
        # javac -d bin src/Main.java && java -cp bin Main --config=2
        ./start.sh 20004 $CANTIDAD_NODOS --logResults --path ../results --nopurge --noremove > resultados.fase2.txt 2>&1
        ;;
    3)
        echo "Opción 3 recibida. Lanzando comando para el entorno 3..."
        # Sustituye la siguiente línea por tu comando real
        # javac -d bin src/Main.java && java -cp bin Main --config=3
        ./start.sh 20004 $CANTIDAD_NODOS --logResults --path ../results --noremove > resultados.fase3.txt 2>&1
        ;;
    4)
        echo "Opción 4 recibida. Lanzando comando para el entorno 4..."
        # Sustituye la siguiente línea por tu comando real
        # javac -d bin src/Main.java && java -cp bin Main --config=4
        ./start.sh 20004 $CANTIDAD_NODOS --logResults --path ../results  > resultados.fase4.txt 2>&1
        ;;
    *)
        # Esta opción (*) actúa como "default" y captura cualquier valor no contemplado
        echo "Error: Parámetro no válido ('$OPCION_FASE')."
        echo "Los valores permitidos son únicamente 2, 3 o 4."
        exit 1
        ;;
esac

cd ..

echo "-*---------------------------------------Comprobar resultados."
ls  -lh bin/recipes_service/ServerData.class bin/recipes_service/tsae/data_structures/* bin/recipes_service/tsae/sessions/*  scripts/Results*; cat scripts/Results 
date
echo "-*---------------------------------------Ejecución finalizada."


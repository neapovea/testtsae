#!/bin/bash

echo "-*---------------------------------------Probando FASE." $OPCION_FASE
# 1. Comprobar si se ha pasado al menos un parámetro
if [ -z "$OPCION_FASE" ]; then
    echo "Error: No se ha proporcionado parámetro OPCION_FASE."
    echo "Uso: $0 <2|3|4>"
    exit 1
fi

if [ -z "$CANTIDAD_NODOS" ]; then
    echo "Error: No se ha proporcionado parámetro CANTIDAD_NODOS."
    echo "Uso: $0 <6|15|20>"
    exit 1
fi

## segun la fase hago checkuout sobre la rama en concreto
echo "Opción $OPCION_FASE recibida. Lanzando git sobre fase $OPCION_FASE"
case "$OPCION_FASE" in
    2)
        git checkout FASE2
        ;;
    3)
        git checkout FASE3
        ;;
    4)
        git checkout FASE4
        ;;
    *)
        # Aquí nunca llega
        echo "Error: Parámetro no válido ('$OPCION_FASE')."
        echo "Los valores permitidos son únicamente 2, 3 o 4."
        exit 1
        ;;
esac

echo "-*---------------------------------------Compilar."
# COMPILAR
find src -name "*.java" > sources.txt
javac -version
javac --release 17 -d bin -cp "lib/*" @sources.txt


# MOSTRAR RESULTAdos
echo "-*---------------------------------------Comprobar compilación."
echo "-*---------------------------------------Probando FASE." $OPCION_FASE
ls  -lh bin/recipes_service/ServerData.class bin/recipes_service/tsae/data_structures/* bin/recipes_service/tsae/sessions/*  scripts/Results*; cat scripts/Results 






cd scripts
chmod +x  start.sh 

# 3. Evaluar el parámetro y lanzar el comando correspondiente
echo "Opción $OPCION_FASE recibida. Lanzando comando para fase $OPCION_FASE con  $CANTIDAD_NODOS  nodos"
date
case "$OPCION_FASE" in
    2)
        ./start.sh 20004 $CANTIDAD_NODOS --logResults --path ../results --nopurge --noremove > resultados.fase2.txt 2>&1
        ;;
    3)
        ./start.sh 20004 $CANTIDAD_NODOS --logResults --path ../results --noremove > resultados.fase3.txt 2>&1
        ;;
    4)
        ./start.sh 20004 $CANTIDAD_NODOS --logResults --path ../results  > resultados.fase4.txt 2>&1
        ;;
    *)
        # Aquí nunca llega
        echo "Error: Parámetro no válido ('$OPCION_FASE')."
        echo "Los valores permitidos son únicamente 2, 3 o 4."
        exit 1
        ;;
esac

cd ..

echo "-*---------------------------------------Comprobar resultados."
date
ls  -lh bin/recipes_service/ServerData.class bin/recipes_service/tsae/data_structures/* bin/recipes_service/tsae/sessions/*  scripts/Results*; cat scripts/Results 
echo "-*---------------------------------------Ejecución finalizada."

# vuelvo a rama main
git checkout main

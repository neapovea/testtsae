/*
* Copyright (c) Joan-Manuel Marques 2013. All rights reserved.
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
*
* This file is part of the practical assignment of Distributed Systems course.
*
* This code is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This code is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this code.  If not, see <http://www.gnu.org/licenses/>.
*/

package recipes_service.tsae.data_structures;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import recipes_service.data.Operation;
//LSim logging system imports sgeag@2017
//import lsim.coordinator.LSimCoordinator;
import edu.uoc.dpcs.lsim.logger.LoggerManager.Level;
import lsim.library.api.LSimLogger;

/**
 * @author Joan-Manuel Marques, Daniel Lázaro Iglesias
 * December 2012
 *
 */
public class Log implements Serializable{
	// Only for the zip file with the correct solution of phase1.Needed for the logging system for the phase1. sgeag_2018p 
//	private transient LSimCoordinator lsim = LSimFactory.getCoordinatorInstance();
	// Needed for the logging system sgeag@2017
//	private transient LSimWorker lsim = LSimFactory.getWorkerInstance();

	private static final long serialVersionUID = -4864990265268259700L;
	/**
	 * This class implements a log, that stores the operations
	 * received  by a client.
	 * They are stored in a ConcurrentHashMap (a hash table),
	 * that stores a list of operations for each member of 
	 * the group.
	 */
	private final ConcurrentHashMap<String, CopyOnWriteArrayList<Operation>> log = new ConcurrentHashMap<>();
	private final ReadWriteLock lock = new ReentrantReadWriteLock();


	public Log(List<String> participants){
		// create an empty log
		for (String participant : participants) {
			log.put(participant, new CopyOnWriteArrayList<>());
		}
	}

	/**
	 * inserts an operation into the log. Operations are 
	 * inserted in order. If the last operation for 
	 * the user is not the previous operation than the one 
	 * being inserted, the insertion will fail.
	 * 
	 * @param op
	 * @return true if op is inserted, false otherwise.
	 */
	public synchronized boolean add(Operation op){
		// Adquirimos el candado de escritura
		lock.writeLock().lock();
		try {
			// Recuperar el ID del host
			String hostId = op.getTimestamp().getHostid();

			// Obtener o inicializar la lista de operaciones para este host
			List<Operation> operations = log.computeIfAbsent(hostId, key -> new CopyOnWriteArrayList<>());

			// Si es la primera operación del host, se añade directamente
			if (operations.isEmpty()) {
				return operations.add(op);
			}

			// Extraer el último timestamp para una comparación más clara
			Timestamp lastTimestamp = operations.get(operations.size() - 1).getTimestamp();
			Timestamp newTimestamp = op.getTimestamp();

			// Solo se añade si el nuevo timestamp es estrictamente mayor
			if (lastTimestamp.compare(newTimestamp) < 0) {
				operations.add(op);
				return true;
			}
			return false;
		} finally {
			// Siempre liberar en el bloque finally
			lock.writeLock().unlock();
		}

	}

	
	
	/**
	 * @param sum The sum of timestamps to compare against.
	 * @return A list of operations that are newer than the given sum of timestamps.
	 */
	public List<Operation> listNewer(TimestampVector partnerSummary) {
		List<Operation> newOperations = new ArrayList<>();

		lock.readLock().lock();
		try {
			// Iterar sobre las entradas del log (operaciones por host)
			for (var entry : log.entrySet()) {
				String hostId = entry.getKey();
				List<Operation> hostOperations = entry.getValue();

				// Obtener el punto de corte del compañero para este host específico
				Timestamp lastSeenByPartner = partnerSummary.getLast(hostId);

				// Filtrar y añadir solo las operaciones que el compañero no ha visto
				for (Operation op : hostOperations) {
					if (op.getTimestamp().compare(lastSeenByPartner) > 0) {
						newOperations.add(op);
					}
				}
			}
		} finally {
			lock.readLock().unlock();
		}

		return newOperations;
	}
	
	/**
	 * Removes from the log the operations that have
	 * been acknowledged by all the members
	 * of the group, according to the provided
	 * ackSummary. 
	 * @param ack: ackSummary.
	 */
	public void purgeLog(TimestampMatrix ack){
		lock.writeLock().lock();
		try {
			// Iteramos sobre cada lista de operaciones agrupadas por el emisor (hostId)
			for (var entry : log.entrySet()) {
				String senderId = entry.getKey();
				List<Operation> operations = entry.getValue();

				// Obtenemos la fila de la matriz ACK que registra el progreso de todos para este emisor
				TimestampVector ackRow = ack.getTimestampVector(senderId);

				// Eliminamos de la lista local los mensajes confirmados globalmente
				operations.removeIf(op -> {
					// Según TSAE (relojes no sincr.), un mensaje es purgable si su tiempo 't'
					// es <= que todas las entradas en la fila del emisor en la matriz ACK.
					Timestamp msgTs = op.getTimestamp();
					Timestamp minGlobalProgress = ackRow.getLast(msgTs.getHostid());

					return msgTs.compare(minGlobalProgress) <= 0;
				});
			}
		} finally {
			lock.writeLock().unlock();
		}

	}

	/**
	 * equals
	 */
	@Override
	public boolean equals(Object obj) {
		// Verificar de identidad y nulidad básica
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;

		// Casting y sección crítica protegida
		Log other = (Log) obj;
		lock.readLock().lock();
		try {
			// Comparamos el contenido del mapa interno (ConcurrentHashMap)
			return this.log.equals(other.log);
		} finally {
			lock.readLock().unlock();
		}
	}



	/**
	 * toString
	 */
	@Override
	public String toString() {
		lock.readLock().lock();
		try {
			// Usamos Streams para aplanar el mapa y unir las operaciones con saltos de línea
			return log.values().stream()
					.flatMap(List::stream)
					.map(Operation::toString)
					.collect(Collectors.joining("\n", "", "\n"));
		} finally {
			lock.readLock().unlock();
		}
//
//		lock.readLock().lock();
//		try {
//			StringBuilder sb = new StringBuilder();
//			for (CopyOnWriteArrayList<Operation> sublog : log.values()) {
//				for (Operation op : sublog) {
//					sb.append(op.toString()).append("\n");
//				}
//			}
//			return sb.toString();
//		} finally {
//			lock.readLock().unlock();
//		}
//	}

	}


}

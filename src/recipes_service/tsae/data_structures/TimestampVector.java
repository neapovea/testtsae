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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import edu.uoc.dpcs.lsim.logger.LoggerManager.Level;
import lsim.library.api.LSimLogger;
import recipes_service.data.Operation;

/**
 * @author Joan-Manuel Marques
 * December 2012
 *
 */
public class TimestampVector implements Serializable{
	// Only for the zip file with the correct solution of phase1.Needed for the logging system for the phase1. sgeag_2018p 
//	private transient LSimCoordinator lsim = LSimFactory.getCoordinatorInstance();
	// Needed for the logging system sgeag@2017
//	private transient LSimWorker lsim = LSimFactory.getWorkerInstance();
	
	private static final long serialVersionUID = -765026247959198886L;
	/**
	 * This class stores a summary of the timestamps seen by a node.
	 * For each node, stores the timestamp of the last received operation.
	 */
	
	private final ConcurrentHashMap<String, Timestamp> timestampVector= new ConcurrentHashMap<String, Timestamp>();
	private final ReadWriteLock lock = new ReentrantReadWriteLock();

	public TimestampVector (List<String> participants){
		lock.writeLock().lock();
		try {// create and empty TimestampVector
			for (Iterator<String> it = participants.iterator(); it.hasNext(); ){
				String id = it.next();
				// when sequence number of timestamp < 0 it means that the timestamp is the null timestamp
				timestampVector.put(id, new Timestamp(id, Timestamp.NULL_TIMESTAMP_SEQ_NUMBER));
			}
		} finally {
			// Libera bloqueo
			lock.writeLock().unlock();
		}
	}



	/**
	 * Updates the timestamp vector with a new timestamp. 
	 * @param timestamp
	 */
	public void updateTimestamp(Timestamp newTimestamp) {
		// Validación temprana
		if (newTimestamp == null) {
			LSimLogger.log(Level.WARN, "Attempted to update TimestampVector with a null timestamp.");
			return;
		}

		// Adquisición del candado de escritura (Acción de modificación)
		lock.writeLock().lock();
		try {
			String hostId = newTimestamp.getHostid();
			Timestamp currentTimestamp = timestampVector.get(hostId);

			// Lógica TSAE: Actualizar solo si es estrictamente posterior (máximo elemento)
			if (currentTimestamp == null || newTimestamp.compare(currentTimestamp) > 0) {
				timestampVector.put(hostId, newTimestamp);

				LSimLogger.log(Level.TRACE, "Updated Summary Vector for " + hostId + " to " + newTimestamp);
			} else {
				LSimLogger.log(Level.TRACE, "Skipped update for " + hostId
						+ ". Known: " + currentTimestamp + ", Received: " + newTimestamp);
			}
		} finally {
			// Libera bloqueo
			lock.writeLock().unlock();
		}
	}
	
	/**
	 * merge in another vector, taking the elementwise maximum
	 * @param tsVector (a timestamp vector)
	 */
	public void updateMax(TimestampVector other) {
		// Validación de nulidad
		if (other == null) return;

		// Adquisición del candado de escritura
		lock.writeLock().lock();
		try {
			// Fusión de vectores usando el máximo elemento a elemento
			other.timestampVector.forEach((hostId, incomingTS) -> {
				if (incomingTS != null) {
					timestampVector.merge(hostId, incomingTS, (localTS, incoming) ->
							(incoming.compare(localTS) > 0) ? incoming : localTS
					);
				}
			});
		} finally {
			// Libera bloqueo
			lock.writeLock().unlock();
		}
	}
	
	/**
	 * 
	 * @param node
	 * @return the last timestamp issued by node that has been
	 * received.
	 */
	public Timestamp getLast(String node){
		return timestampVector.get(node);
	}
	
	/**
	 * merges local timestamp vector with tsVector timestamp vector taking
	 * the smallest timestamp for each node.
	 * After merging, local node will have the smallest timestamp for each node.
	 *  @param tsVector (timestamp vector)
	 */
	public void mergeMin(TimestampVector other) {
		// Validación de nulidad
		if (other == null) return;

		// Adquisición del candado de escritura
		lock.writeLock().lock();
		try {
			// 3. Fusión de vectores usando el mínimo elemento a elemento
			other.timestampVector.forEach((hostId, incomingTS) -> {
				if (incomingTS != null) {
					timestampVector.merge(hostId, incomingTS, (localTS, incoming) ->
							(incoming.compare(localTS) < 0) ? incoming : localTS
					);
				}
			});
		} finally {
			// Libera bloqueo
			lock.writeLock().unlock();
		}
	}
	
	/**
	 * clone
	 */
	@Override
	public TimestampVector clone() {
		lock.readLock().lock();
		try {
			// Crear una lista de los participantes actuales de forma más limpia
			var participants = List.copyOf(this.timestampVector.keySet());

			// Instanciar el nuevo vector (el constructor lo inicializa con valores nulos)
			TimestampVector cloned = new TimestampVector(participants);

			// Copiar profundamente los estados actuales (timestamps) al nuevo vector
			cloned.timestampVector.putAll(this.timestampVector);

			return cloned;
		} finally {
			// Libera bloqueo
			lock.readLock().unlock();
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
		TimestampVector other = (TimestampVector) obj;

		lock.readLock().lock();
		try {
			TimestampVector comp = (TimestampVector) obj;
			return timestampVector.equals(comp.timestampVector);
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
			// Usamos Streams para transformar cada Timestamp en String y unirlos con saltos de línea
			return timestampVector.values().stream()
					.map(ts -> ts != null ? ts.toString() : "null")
					.collect(Collectors.joining("\n", "", "\n"));
		} finally {
			lock.readLock().unlock();
		}
	}
}

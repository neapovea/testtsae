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

package recipes_service;

import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import edu.uoc.dpcs.lsim.logger.LoggerManager.Level;
import lsim.library.api.LSimLogger;
import recipes_service.activity_simulation.SimulationData;
import recipes_service.communication.Host;
import recipes_service.communication.Hosts;
import recipes_service.data.AddOperation;
import recipes_service.data.Operation;
import recipes_service.data.OperationType;
import recipes_service.data.Recipe;
import recipes_service.data.Recipes;
import recipes_service.data.RemoveOperation;
import recipes_service.tsae.data_structures.Log;
import recipes_service.tsae.data_structures.Timestamp;
import recipes_service.tsae.data_structures.TimestampMatrix;
import recipes_service.tsae.data_structures.TimestampVector;
import recipes_service.tsae.sessions.TSAESessionOriginatorSide;
/**
 * @author Joan-Manuel Marques
 * December 2012
 *
 */
public class ServerData {
	
	// server id
	private String id;
	
	// sequence number of the last recipe timestamped by this server
	private AtomicLong seqnum = new AtomicLong(Timestamp.NULL_TIMESTAMP_SEQ_NUMBER);


	// timestamp lock
	private Object timestampLock = new Object();
	
	// TSAE data structures
	private Log log = null;
	private TimestampVector summary = null;
	private TimestampMatrix ack = null;
	
	// recipes data structure
	private Recipes recipes = new Recipes();

	// number of TSAE sessions
	int numSes = 1; // number of different partners that a server will contact for a TSAE session each time that TSAE timer (each sessionPeriod seconds) expires

	// propDegree: (default value: 0) number of TSAE sessions done each time a new data is created
	int propDegree = 0;
	
	// Participating nodes
	private Hosts participants;

	// TSAE timers
	private long sessionDelay;
	private long sessionPeriod = 10;

	private Timer tsaeSessionTimer;

	//
	TSAESessionOriginatorSide tsae = null;

	// TODO: esborrar aquesta estructura de dades
	// tombstones: timestamp of removed operations
	//List<Timestamp> tombstones = new Vector<Timestamp>();
	private final List<Timestamp> tombstones = new CopyOnWriteArrayList<>();

	private final ReadWriteLock lock = new ReentrantReadWriteLock();

	// end: true when program should end; false otherwise
	private boolean end;

	public ServerData(){
	}
	
	/**
	 * Starts the execution
	 * @param participantss
	 */
	public void startTSAE(Hosts participants){
		this.participants = participants;
		this.log = new Log(participants.getIds());
		this.summary = new TimestampVector(participants.getIds());
		this.ack = new TimestampMatrix(participants.getIds());
		

		//  Sets the Timer for TSAE sessions
	    tsae = new TSAESessionOriginatorSide(this);
		tsaeSessionTimer = new Timer();
		tsaeSessionTimer.scheduleAtFixedRate(tsae, sessionDelay, sessionPeriod);
	}

	public void stopTSAEsessions(){
		if (tsaeSessionTimer != null) {
			tsaeSessionTimer.cancel();
		}
	}
	
	public boolean end(){
		return this.end;
	}
	
	public void setEnd(){
		this.end = true;
	}

	// ******************************
	// *** timestamps
	// ******************************
	private Timestamp nextTimestamp(){
		Timestamp nextTimestamp = null;

		// Adquirir el candado de escritura para asegurar exclusividad total
		lock.writeLock().lock();
		try {
			// Lógica de inicialización/reinicio de la secuencia
			if (seqnum.get() == Timestamp.NULL_TIMESTAMP_SEQ_NUMBER) {
				seqnum.set(-1);
			}

			// Generar el nuevo timestamp con el ID del host y el incremento atómico
			nextTimestamp = new Timestamp(id, seqnum.incrementAndGet());

		} finally {
			// Liberar siempre el candado en el bloque finally
			lock.writeLock().unlock();
		}

		return nextTimestamp;
	}

	// ******************************
	// *** add and remove recipes
	// ******************************
	public void addRecipe(String recipeTitle, String recipe) {
		// Validación de entrada (fuera del bloqueo para mayor eficiencia)
		if (recipeTitle == null || recipe == null) {
			LSimLogger.log(Level.WARN, "Invalid recipe input: title or content is null.");
			return;
		}

		// Adquisición del candado de escritura
		// Garantiza que ninguna sesión de anti-entropía lea datos inconsistentes mientras se añade la receta
		lock.writeLock().lock();
		try {
			// Generación de metadatos del protocolo TSAE
			// El próximo timestamp válido se obtiene mediante nextTimestamp()
			Timestamp timestamp = nextTimestamp();

			// Creación de la receta y la operación de suma
			Recipe rcpe = new Recipe(recipeTitle, recipe, id, timestamp);
			Operation op = new AddOperation(rcpe, timestamp);

			// Actualización atómica de las estructuras de datos del servidor [1]
			log.add(op);                        // Añadir al log de mensajes para propagación
			summary.updateTimestamp(timestamp); // Actualizar el conocimiento local del host
			recipes.add(rcpe);                 // Actualizar el estado de la aplicación local

			LSimLogger.log(Level.TRACE, "Recipe '" + recipeTitle + "' added to local storage and log.");

		} finally {
			// Liberación garantizada del candado en el bloque finally
			lock.writeLock().unlock();
		}
	}
	
	public synchronized void removeRecipe(String recipeTitle){
		System.err.println("Error: removeRecipe method (recipesService.serverData) not yet implemented");
	}

	private void purgeTombstones() {
		// Validar nulidad
		if (ack == null) return;

		lock.writeLock().lock();
		try {
			// Obtener el vector que representa el progreso mínimo confirmado por todos
			var globalProgressCut = ack.minTimestampVector();

			// Eliminar tombstones que ya han sido observadas por todos los miembros
			tombstones.removeIf(ts -> ts.compare(globalProgressCut.getLast(ts.getHostid())) <= 0);

		} finally {
			// 4. Liberación garantizada del candado
			lock.writeLock().unlock();
		}
	}


	// ****************************************************************************
	// *** operations to get the TSAE data structures. Used to send to evaluation
	// ****************************************************************************
	public Log getLog() {
		return log;
	}
	public TimestampVector getSummary() {
		return summary;
	}
	public TimestampMatrix getAck() {
		return ack;
	}
	public Recipes getRecipes(){
		return recipes;
	}

	// ******************************
	// *** getters and setters
	// ******************************
	public void setId(String id){
		this.id = id;		
	}
	public String getId(){
		return this.id;
	}

	public int getNumberSessions(){
		return numSes;
	}

	public void setNumberSessions(int numSes){
		this.numSes = numSes;
	}

	public int getPropagationDegree(){
		return this.propDegree;
	}

	public void setPropagationDegree(int propDegree){
		this.propDegree = propDegree;
	}

	public void setSessionDelay(long sessionDelay) {
		this.sessionDelay = sessionDelay;
	}
	public void setSessionPeriod(long sessionPeriod) {
		this.sessionPeriod = sessionPeriod;
	}
	public TSAESessionOriginatorSide getTSAESessionOriginatorSide(){
		return this.tsae;
	}
	
	// ******************************
	// *** other
	// ******************************
	
	public List<Host> getRandomPartners(int num){
		return participants.getRandomPartners(num);
	}
	
	/**
	 * waits until the Server is ready to receive TSAE sessions from partner servers   
	 */
	public synchronized void waitServerConnected(){
		while (!SimulationData.getInstance().isConnected()){
			try {
				wait();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				//			e.printStackTrace();
			}
		}
	}
	
	/**
	 * 	Once the server is connected notifies to ServerPartnerSide that it is ready
	 *  to receive TSAE sessions from partner servers  
	 */ 
	public synchronized void notifyServerConnected(){
		notifyAll();
	}


	public void execOperation(Operation op) {
		// Adquirir candado de escritura para proteger la modificación de datos
		lock.writeLock().lock();
		try {
			// Procesar operación de adición usando Pattern Matching
			if (op instanceof AddOperation addOp) {
				Recipe recipeData = addOp.getRecipe();

				// Crear una nueva instancia de la receta para asegurar la integridad local
				Recipe newRecipe = new Recipe(
						recipeData.getTitle(),
						recipeData.getRecipe(),
						recipeData.getAuthor(),
						recipeData.getTimestamp()
				);

				recipes.add(newRecipe);
				log.add(op); // Registrar en el log para futura propagación

			}
			// Procesar operación de eliminación
			else if (op instanceof RemoveOperation removeOp) {
				recipes.remove(removeOp.getRecipeTitle());
			}
		} finally {
			// Asegurar la liberación del candado siempre
			lock.writeLock().unlock();
		}
	}
}

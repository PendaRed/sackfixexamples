/**
 * Created by Jonathan in 2016.
 *
 * The acceptor - ie clients will connect to my sockets.  Only clients configured in
 * application.conf will be accepted.
 *
 * SackFixServer
 *      The main server class to start up the Actor system
 * SfServerGuardianActor
 *      Handles all system exception and lifecycle.  Failfast is preferred
 * OmsMessageInActor
 *      Your code goes here, write your trading application/OMS/reconciler/whatever
 *
 * When writing your own system you may want to replace the file based stores for messages
 * with something else.  Sackfix uses the stores to replay messages should the initiator send
 * a resend request.  The interfaces you should implement would be
 *
 * SfMessageStore
 * SessionOpenTodayStore
 */
package org.sackfix.server;
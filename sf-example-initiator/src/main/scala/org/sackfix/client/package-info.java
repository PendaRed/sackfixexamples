/**
 * Created by Jonathan in 2016.
 *
 * The initiator - ie I am a client connecting to a server/acceptor.
 *
 * SackFixClient
 *      The main client class to start up the Actor system
 * SfClientGuardianActor
 *      Handles all system exception and lifecycle.  Failfast is preferred
 * ClientOmsMessageActor
 *      Your code goes here, write your trading application/OMS/reconciler/whatever
 *
 * When writing your own system you may want to replace the file based stores for messages
 * with something else.  Sackfix uses the stores to replay messages should the acceptor send
 * a resend request.  The interfaces you should implement would be
 *
 * SfMessageStore
 * SessionOpenTodayStore
 */
package org.sackfix.client;
/*
 * Copyright (c) 2002-2025, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.appointment.service.listeners;

import fr.paris.lutece.plugins.appointment.service.AppointmentExecutorService;
import fr.paris.lutece.portal.service.spring.SpringContextService;

public final class FormListenerManager
{

    /**
     * Private default constructor
     */
    private FormListenerManager( )
    {
        // Nothing to do
    }

    /**
     * Notify listeners that a form has been changed
     * 
     * @param nIdForm
     *            The id of the form that has been changed
     */
    public static void notifyListenersFormCreation( int nIdForm )
    {
        AppointmentExecutorService.INSTANCE.execute( ( ) -> {
            for ( IFormListener formListener : SpringContextService.getBeansOfType( IFormListener.class ) )
            {
                formListener.notifyFormCreation( nIdForm );
            }
        } );
    }

    /**
     * Notify listeners that a form has been
     * 
     * @param nIdForm
     *            The id of the form that has been changed
     */
    public static void notifyListenersFormChange( int nIdForm )
    {
        AppointmentExecutorService.INSTANCE.execute( ( ) -> {
            for ( IFormListener formListener : SpringContextService.getBeansOfType( IFormListener.class ) )
            {
                formListener.notifyFormChange( nIdForm );
            }
        } );
    }

    /**
     * Notify listeners that a form is about to be removed
     * 
     * @param nIdForm
     *            The id of the form that will be removed
     */
    public static void notifyListenersFormRemoval( int nIdForm )
    {
        AppointmentExecutorService.INSTANCE.execute( ( ) -> {
            for ( IFormListener formListener : SpringContextService.getBeansOfType( IFormListener.class ) )
            {
                formListener.notifyFormRemoval( nIdForm );
            }
        } );
    }

}

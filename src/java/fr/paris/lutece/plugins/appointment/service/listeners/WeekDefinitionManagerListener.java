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

import java.util.List;

import fr.paris.lutece.plugins.appointment.business.planning.WeekDefinition;
import fr.paris.lutece.plugins.appointment.service.AppointmentExecutorService;
import fr.paris.lutece.portal.service.spring.SpringContextService;

public final class WeekDefinitionManagerListener
{

    /**
     * Private default constructor
     */
    private WeekDefinitionManagerListener( )
    {
        // Nothing to do
    }

    /**
     * Notify listeners that a week definition has been created
     * 
     * @param weekDefinition
     *            The week definition that has been assigned
     */
    public static void notifyListenersWeekDefinitionAssigned( WeekDefinition weekDefinition )
    {
        AppointmentExecutorService.INSTANCE.execute( ( ) -> {
            for ( IWeekDefinitionListener weekDefinitionListener : SpringContextService.getBeansOfType( IWeekDefinitionListener.class ) )
            {
                weekDefinitionListener.notifyWeekAssigned( weekDefinition );
            }
        } );
    }

    /**
     * Notify listeners that a list of Week Definition has been changed (assign and unasign)
     * 
     * @param nIdForm
     *            The id of the form where the Week Definition has been changed
     * @param listWeek
     *            the list of week
     */
    public static void notifyListenersListWeekDefinitionChanged( int nIdForm, List<WeekDefinition> listWeek )
    {
        AppointmentExecutorService.INSTANCE.execute( ( ) -> {
            for ( IWeekDefinitionListener weekDefinitionListener : SpringContextService.getBeansOfType( IWeekDefinitionListener.class ) )
            {
                weekDefinitionListener.notifyListWeeksChanged( nIdForm, listWeek );
            }
        } );
    }

    /**
     * Notify listeners that a Week Definition is about to be removed
     * 
     * @param weekDefinition
     *            The week definition that has been assigned
     */
    public static void notifyListenersWeekDefinitionUnassigned( WeekDefinition weekDefinition )
    {
        AppointmentExecutorService.INSTANCE.execute( ( ) -> {
            for ( IWeekDefinitionListener weekDefinitionListener : SpringContextService.getBeansOfType( IWeekDefinitionListener.class ) )
            {
                weekDefinitionListener.notifyWeekUnassigned( weekDefinition );
            }
        } );
    }

}

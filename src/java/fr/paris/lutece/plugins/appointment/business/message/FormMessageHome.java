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
package fr.paris.lutece.plugins.appointment.business.message;

import fr.paris.lutece.plugins.appointment.service.AppointmentPlugin;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.spring.SpringContextService;

/**
 * This class provides instances management methods for Form Message objects
 * 
 * @author Laurent Payen
 *
 */
public final class FormMessageHome
{
    // Static variable pointed at the DAO instance
    private static IFormMessageDAO _dao = SpringContextService.getBean( "appointment.formMessageDAO" );
    private static Plugin _plugin = PluginService.getPlugin( AppointmentPlugin.PLUGIN_NAME );

    /**
     * Private constructor - this class need not be instantiated
     */
    private FormMessageHome( )
    {
    }

    /**
     * Create a form message
     * 
     * @param formMessage
     *            The instance of the form message to create
     */
    public static void create( FormMessage formMessage )
    {
        _dao.insert( formMessage, _plugin );
    }

    /**
     * Update a form message
     * 
     * @param formMessage
     *            The form message to update
     */
    public static void update( FormMessage formMessage )
    {
        _dao.update( formMessage, _plugin );
    }

    /**
     * Delete a form message from its primary key
     * 
     * @param nFormMessageId
     *            The id of the form message
     */
    public static void delete( int nFormMessageId )
    {
        _dao.delete( nFormMessageId, _plugin );
    }

    /**
     * Delete a form message whose id from is specified in the param
     * 
     * @param nIForm
     *            The id of the form
     */
    public static void deleteByIdForm( int nForm )
    {
        _dao.deleteByIdForm( nForm, _plugin );
    }

    /**
     * Get a form message from its primary key
     * 
     * @param nFormMessageId
     *            The id of the form message
     * @return The form message, or null if no form message has the given primary key
     */
    public static FormMessage findByPrimaryKey( int nFormMessageId )
    {
        return _dao.select( nFormMessageId, _plugin );
    }

    /**
     * Returns the formMessage of the form
     * 
     * @param nIdForm
     *            the form id
     * @return the formMessage of the form
     */
    public static FormMessage findByIdForm( int nIdForm )
    {
        return _dao.findByIdForm( nIdForm, _plugin );
    }
}

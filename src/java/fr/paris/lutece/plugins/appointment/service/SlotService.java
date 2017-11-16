package fr.paris.lutece.plugins.appointment.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;

import fr.paris.lutece.plugins.appointment.business.planning.TimeSlot;
import fr.paris.lutece.plugins.appointment.business.planning.WeekDefinition;
import fr.paris.lutece.plugins.appointment.business.planning.WorkingDay;
import fr.paris.lutece.plugins.appointment.business.rule.ReservationRule;
import fr.paris.lutece.plugins.appointment.business.slot.Period;
import fr.paris.lutece.plugins.appointment.business.slot.Slot;
import fr.paris.lutece.plugins.appointment.business.slot.SlotHome;
import fr.paris.lutece.plugins.appointment.service.listeners.SlotListenerManager;

/**
 * Service class of a slot
 * 
 * @author Laurent Payen
 *
 */
public final class SlotService
{

    /**
     * Private constructor - this class does not need to be instantiated
     */
    private SlotService( )
    {
    }

    /**
     * Find slots of a form on a given period of time
     * 
     * @param nIdForm
     *            the form Id
     * @param startingDateTime
     *            the starting date time to search
     * @param endingDateTime
     *            the ending date time to search
     * @return a HashMap with the starting Date Time in Key and the corresponding slot in value
     */
    public static HashMap<LocalDateTime, Slot> findSlotsByIdFormAndDateRange( int nIdForm, LocalDateTime startingDateTime, LocalDateTime endingDateTime )
    {
        return SlotHome.findByIdFormAndDateRange( nIdForm, startingDateTime, endingDateTime );
    }

    /**
     * Fins all the slots of a form
     * 
     * @param nIdForm
     *            the form id
     * @return a list of all the slots of a form
     */
    public static List<Slot> findListSlot( int nIdForm )
    {
        return SlotHome.findByIdForm( nIdForm );
    }

    /**
     * Find the open slots of a form on a given period of time
     * 
     * @param nIdForm
     *            the form Id
     * @param startingDateTime
     *            the starting Date time to search
     * @param endingDateTime
     *            the ending Date time to search
     * @return a list of open slots whose matches the criteria
     */
    public static List<Slot> findListOpenSlotByIdFormAndDateRange( int nIdForm, LocalDateTime startingDateTime, LocalDateTime endingDateTime )
    {
        return SlotHome.findOpenSlotsByIdFormAndDateRange( nIdForm, startingDateTime, endingDateTime );
    }

    /**
     * Find a slot with its primary key
     * 
     * @param nIdSlot
     *            the slot Id
     * @return the Slot object
     */
    public static Slot findSlotById( int nIdSlot )
    {
        Slot slot = SlotHome.findByPrimaryKey( nIdSlot );
        if ( slot != null )
        {
            SlotService.addDateAndTimeToSlot( slot );
        }
        return slot;
    }

    /**
     * Build all the slot for a period with all the rules (open hours ...) to apply on each day, for each slot
     * 
     * @param nIdForm
     *            the form Id
     * @param mapWeekDefinition
     *            the map of the week definition
     * @param startingDate
     *            the starting date of the period
     * @param nNbWeeksToDisplay
     *            the number of weeks to build
     * @return a list of all the slots built
     */
    public static List<Slot> buildListSlot( int nIdForm, HashMap<LocalDate, WeekDefinition> mapWeekDefinition, LocalDate startingDate, LocalDate endingDate )
    {
        List<Slot> listSlot = new ArrayList<>( );
        // Get all the reservation rules
        final HashMap<LocalDate, ReservationRule> mapReservationRule = ReservationRuleService.findAllReservationRule( nIdForm );
        final List<LocalDate> listDateWeekDefinition = new ArrayList<>( mapWeekDefinition.keySet( ) );
        final List<LocalDate> listDateReservationTule = new ArrayList<>( mapReservationRule.keySet( ) );
        LocalDate closestDateWeekDefinition;
        LocalDate closestDateReservationRule;
        WeekDefinition weekDefinitionToApply;
        ReservationRule reservationRuleToApply;
        LocalDate dateTemp = startingDate;
        int nMaxCapacity;
        DayOfWeek dayOfWeek;
        WorkingDay workingDay;
        LocalTime minTimeForThisDay;
        LocalTime maxTimeForThisDay;
        LocalTime timeTemp;
        LocalDateTime dateTimeTemp;
        Slot slotToAdd;
        TimeSlot timeSlot;
        LocalDate dateToCompare;
        // Need to check if this date is not before the form date creation
        final LocalDate firstDateOfReservationRule = new ArrayList<>( mapReservationRule.keySet( ) ).stream( ).sorted( ).findFirst( ).orElse( null );
        LocalDate startingDateToUse = startingDate;
        if ( startingDate.isBefore( firstDateOfReservationRule ) )
        {
            startingDateToUse = firstDateOfReservationRule;
        }
        // Get all the closing day of this period
        List<LocalDate> listDateOfClosingDay = ClosingDayService.findListDateOfClosingDayByIdFormAndDateRange( nIdForm, startingDateToUse, endingDate );
        // Get all the slot between these two dates
        HashMap<LocalDateTime, Slot> mapSlot = SlotService.findSlotsByIdFormAndDateRange( nIdForm, startingDateToUse.atStartOfDay( ),
                endingDate.atTime( LocalTime.MAX ) );
        mapSlot.values().forEach(SlotService::addDateAndTimeToSlot);

        // Get or build all the event for the period
        while ( !dateTemp.isAfter( endingDate ) )
        {
            dateToCompare = dateTemp;
            // Find the closest date of apply of week definition with the given
            // date
            closestDateWeekDefinition = Utilities.getClosestDateInPast( listDateWeekDefinition, dateToCompare );
            weekDefinitionToApply = mapWeekDefinition.get( closestDateWeekDefinition );
            // Find the closest date of apply of reservation rule with the given
            // date
            closestDateReservationRule = Utilities.getClosestDateInPast( listDateReservationTule, dateToCompare );
            reservationRuleToApply = mapReservationRule.get( closestDateReservationRule );
            nMaxCapacity = 0;
            if ( reservationRuleToApply != null )
            {
                nMaxCapacity = reservationRuleToApply.getMaxCapacityPerSlot( );
            }
            // Get the day of week of the date
            dayOfWeek = dateTemp.getDayOfWeek( );
            // Get the working day of this day of week
            workingDay = null;
            if ( weekDefinitionToApply != null )
            {
                workingDay = WorkingDayService.getWorkingDayOfDayOfWeek( weekDefinitionToApply.getListWorkingDay( ), dayOfWeek );
            }
            if ( workingDay != null )
            {
                minTimeForThisDay = WorkingDayService.getMinStartingTimeOfAWorkingDay( workingDay );
                maxTimeForThisDay = WorkingDayService.getMaxEndingTimeOfAWorkingDay( workingDay );
                // Check if this day is a closing day
                if ( listDateOfClosingDay.contains( dateTemp ) )
                {
                    listSlot.add( buildSlot( nIdForm, new Period( dateTemp.atTime( minTimeForThisDay ), dateTemp.atTime( maxTimeForThisDay ) ), nMaxCapacity,
                            nMaxCapacity, nMaxCapacity, Boolean.FALSE, Boolean.FALSE ) );
                }
                else
                {
                    timeTemp = minTimeForThisDay;
                    // For each slot of this day
                    while ( timeTemp.isBefore( maxTimeForThisDay ) || !timeTemp.equals( maxTimeForThisDay ) )
                    {
                        // Get the LocalDateTime
                        dateTimeTemp = dateTemp.atTime( timeTemp );
                        // Search if there is a slot for this datetime
                        if ( mapSlot.containsKey( dateTimeTemp ) )
                        {
                            slotToAdd = mapSlot.get( dateTimeTemp );
                            timeTemp = slotToAdd.getEndingDateTime( ).toLocalTime( );
                            listSlot.add( slotToAdd );
                        }
                        else
                        {
                            // Search the timeslot
                            timeSlot = TimeSlotService.getTimeSlotInListOfTimeSlotWithStartingTime( workingDay.getListTimeSlot( ), timeTemp );
                            if ( timeSlot != null )
                            {
                                timeTemp = timeSlot.getEndingTime( );
                                int nMaxCapacityToPut = nMaxCapacity;
                                if ( timeSlot.getMaxCapacity( ) != 0 )
                                {
                                    nMaxCapacityToPut = timeSlot.getMaxCapacity( );
                                }
                                slotToAdd = buildSlot( nIdForm, new Period( dateTimeTemp, dateTemp.atTime( timeTemp ) ), nMaxCapacityToPut, nMaxCapacityToPut,
                                        nMaxCapacityToPut, timeSlot.getIsOpen( ), Boolean.FALSE );
                                listSlot.add( slotToAdd );
                            }
                            else
                            {
                                break;
                            }
                        }
                    }
                }
            }
            else
            {
                // This is not a working day
                // We build all the slots closed for this day
                if ( reservationRuleToApply != null && weekDefinitionToApply != null )
                {
                    minTimeForThisDay = WorkingDayService.getMinStartingTimeOfAListOfWorkingDay( weekDefinitionToApply.getListWorkingDay( ) );
                    maxTimeForThisDay = WorkingDayService.getMaxEndingTimeOfAListOfWorkingDay( weekDefinitionToApply.getListWorkingDay( ) );
                    int nDuration = WorkingDayService.getMinDurationTimeSlotOfAListOfWorkingDay( weekDefinitionToApply.getListWorkingDay( ) );
                    if ( minTimeForThisDay != null && maxTimeForThisDay != null )
                    {
                        timeTemp = minTimeForThisDay;
                        // For each slot of this day
                        while ( timeTemp.isBefore( maxTimeForThisDay ) || !timeTemp.equals( maxTimeForThisDay ) )
                        {
                            // Get the LocalDateTime
                            dateTimeTemp = dateTemp.atTime( timeTemp );
                            // Search if there is a slot for this datetime
                            if ( mapSlot.containsKey( dateTimeTemp ) )
                            {
                                slotToAdd = mapSlot.get( dateTimeTemp );
                                timeTemp = slotToAdd.getEndingDateTime( ).toLocalTime( );
                                listSlot.add( slotToAdd );
                            }
                            else
                            {
                                timeTemp = timeTemp.plusMinutes( Long.valueOf( nDuration ) );
                                if ( timeTemp.isAfter( maxTimeForThisDay ) )
                                {
                                    timeTemp = maxTimeForThisDay;
                                }
                                slotToAdd = buildSlot( nIdForm, new Period( dateTimeTemp, dateTemp.atTime( timeTemp ) ), nMaxCapacity, nMaxCapacity,
                                        nMaxCapacity, Boolean.FALSE, Boolean.FALSE );
                                listSlot.add( slotToAdd );
                            }
                        }
                    }
                }
            }
            dateTemp = dateTemp.plusDays( 1 );
        }
        return listSlot;

    }

    /**
     * Build a slot with all its values
     * 
     * @param nIdForm
     *            the form Id
     * @param startingDateTime
     *            the starting date time
     * @param endingDateTime
     *            the ending date time
     * @param nMaxCapacity
     *            the maximum capacity for the slot
     * @param nNbRemainingPlaces
     *            the number of remaining places of the slot
     * @param bIsOpen
     *            true if the slot is open
     * @return the slot built
     */
    public static Slot buildSlot( int nIdForm, Period period, int nMaxCapacity, int nNbRemainingPlaces, int nNbPotentialRemainingPlaces, boolean bIsOpen,
            boolean bIsSpecific )
    {
        Slot slot = new Slot( );
        slot.setIdSlot( 0 );
        slot.setIdForm( nIdForm );
        slot.setStartingDateTime( period.getStartingDateTime( ) );
        slot.setEndingDateTime( period.getEndingDateTime( ) );
        slot.setMaxCapacity( nMaxCapacity );
        slot.setNbRemainingPlaces( nNbRemainingPlaces );
        slot.setNbPotentialRemainingPlaces( nNbPotentialRemainingPlaces );
        slot.setIsOpen( bIsOpen );
        slot.setIsSpecific( bIsSpecific );
        addDateAndTimeToSlot( slot );
        return slot;
    }

    /**
     * To know if it's a specific slot, need to search for a similar time slot
     * 
     * @param slot
     *            the slot
     * @return true if specific
     */
    private static boolean isSpecificSlot( Slot slot )
    {
        LocalDate dateOfSlot = slot.getDate( );
        WeekDefinition weekDefinition = WeekDefinitionService.findWeekDefinitionByIdFormAndClosestToDateOfApply( slot.getIdForm( ), dateOfSlot );
        WorkingDay workingDay = WorkingDayService.getWorkingDayOfDayOfWeek( weekDefinition.getListWorkingDay( ), dateOfSlot.getDayOfWeek( ) );
        List<TimeSlot> listTimeSlot = null;
        if ( workingDay != null )
        {
            listTimeSlot = TimeSlotService.findListTimeSlotByWorkingDay( workingDay.getIdWorkingDay( ) );
        }
        return isSpecificSlot( slot, workingDay, listTimeSlot );
    }

    /**
     * To know if it's a specific slot, need to search for a similar time slot
     * 
     * @param slot
     *            the slot
     * @param workingDay
     *            the working day
     * @param listTimeSlot
     *            the list of time slots
     * @return true if it's a specific slot
     */
    private static boolean isSpecificSlot( Slot slot, WorkingDay workingDay, List<TimeSlot> listTimeSlot )
    {
        boolean bIsSpecific = Boolean.TRUE;
        List<TimeSlot> listMatchTimeSlot = null;
        if ( workingDay == null )
        {
            if ( !slot.getIsOpen( ) )
            {
                bIsSpecific = Boolean.FALSE;
            }
        }
        else
        {
            listMatchTimeSlot = listTimeSlot
                    .stream( )
                    .filter(
                            t -> ( t.getStartingTime( ).equals( slot.getStartingDateTime( ).toLocalTime( ) ) )
                                    && ( t.getEndingTime( ).equals( slot.getEndingDateTime( ).toLocalTime( ) ) ) && ( t.getIsOpen( ) == slot.getIsOpen( ) )
                                    && ( t.getMaxCapacity( ) == slot.getMaxCapacity( ) ) ).collect( Collectors.toList( ) );
            if ( CollectionUtils.isNotEmpty( listMatchTimeSlot ) )
            {
                bIsSpecific = Boolean.FALSE;
            }
        }
        return bIsSpecific;
    }

    /**
     * Update a slot in database and possibly all the slots after (if the ending hour has changed, all the next slots are impacted in case of the user decide to
     * shift the next slots)
     * 
     * @param slot
     *            the slot to update
     * @param bEndingTimeHasChanged
     *            true if the ending time has changed
     * @param bShifSlot
     *            true if the user has decided to shift the next slots
     */
    public static void updateSlot( Slot slot, boolean bEndingTimeHasChanged, boolean bShifSlot )
    {
        slot.setIsSpecific( isSpecificSlot( slot ) );
        List<Slot> listSlotToCreate = new ArrayList<>( );
        if ( bEndingTimeHasChanged )
        {
            if ( !bShifSlot )
            {
                // Need to get all the slots until the new end of this slot
                List<Slot> listSlotToDelete = new ArrayList<>( SlotService.findSlotsByIdFormAndDateRange( slot.getIdForm( ),
                        slot.getStartingDateTime( ).plusMinutes( 1 ), slot.getEndingDateTime( ) ).values( ) );
                deleteListSlot( listSlotToDelete );
                // Get the list of slot after the modified slot
                HashMap<LocalDateTime, Slot> mapNextSlot = SlotService.findSlotsByIdFormAndDateRange( slot.getIdForm( ), slot.getEndingDateTime( ), slot
                        .getDate( ).atTime( LocalTime.MAX ) );
                List<LocalDateTime> listStartingDateTimeNextSlot = new ArrayList<>( mapNextSlot.keySet( ) );
                // Get the next date time slot
                LocalDateTime nextStartingDateTime = null;
                if ( CollectionUtils.isNotEmpty( listStartingDateTimeNextSlot ) )
                {
                    nextStartingDateTime = Utilities.getClosestDateTimeInFuture( listStartingDateTimeNextSlot, slot.getEndingDateTime( ) );
                }
                else
                {
                    LocalDate dateOfSlot = slot.getDate( );
                    WeekDefinition weekDefinition = WeekDefinitionService.findWeekDefinitionByIdFormAndClosestToDateOfApply( slot.getIdForm( ), dateOfSlot );
                    WorkingDay workingDay = WorkingDayService.getWorkingDayOfDayOfWeek( weekDefinition.getListWorkingDay( ), dateOfSlot.getDayOfWeek( ) );
                    // No slot after this one.
                    // Need to compute between the end of this slot and the next
                    // time slot
                    if ( workingDay != null )
                    {
                        List<TimeSlot> nextTimeSlots = TimeSlotService.getNextTimeSlotsInAListOfTimeSlotAfterALocalTime( workingDay.getListTimeSlot( ),
                                slot.getEndingTime( ) );
                        TimeSlot nextTimeSlot = null;
                        if ( CollectionUtils.isNotEmpty( nextTimeSlots ) )
                        {
                            nextTimeSlot = nextTimeSlots.stream( ).min( ( t1, t2 ) -> t1.getStartingTime( ).compareTo( t2.getStartingTime( ) ) ).get( );
                        }
                        if ( nextTimeSlot != null )
                        {
                            nextStartingDateTime = nextTimeSlot.getStartingTime( ).atDate( dateOfSlot );
                        }
                    }
                    else
                    {
                        // This is not a working day
                        // Generated the new slots at the end of the modified
                        // slot
                        listSlotToCreate.addAll( generateListSlotToCreateAfterATime( slot.getEndingDateTime( ), slot.getIdForm( ) ) );
                    }
                }
                // Need to create a slot between these two dateTime
                if ( nextStartingDateTime != null && !slot.getEndingDateTime( ).isEqual( nextStartingDateTime ) )
                {
                    Slot slotToCreate = buildSlot( slot.getIdForm( ), new Period( slot.getEndingDateTime( ), nextStartingDateTime ), slot.getMaxCapacity( ),
                            slot.getMaxCapacity( ), slot.getMaxCapacity( ), Boolean.FALSE, Boolean.TRUE );
                    listSlotToCreate.add( slotToCreate );
                }
            }
            else
            {
                // Need to delete the slot until the end of the day
                List<Slot> listSlotToDelete = new ArrayList<>( SlotService.findSlotsByIdFormAndDateRange( slot.getIdForm( ),
                        slot.getStartingDateTime( ).plus( 1, ChronoUnit.MINUTES ), slot.getDate( ).atTime( LocalTime.MAX ) ).values( ) );
                deleteListSlot( listSlotToDelete );
                // Generated the new slots at the end of the modified slot
                listSlotToCreate.addAll( generateListSlotToCreateAfterATime( slot.getEndingDateTime( ), slot.getIdForm( ) ) );
            }
        }
        // If it's an update of an existing slot
        if ( slot.getIdSlot( ) != 0 )
        {
            Slot oldSlot = SlotService.findSlotById( slot.getIdSlot( ) );
            int nNbMaxCapacity = slot.getMaxCapacity( );
            // If the max capacity has been modified
            if ( nNbMaxCapacity != oldSlot.getMaxCapacity( ) )
            {
                // Need to update the remaining places
                // We can update the values without keeping the old values
                // because a check has been done before regarding the
                // appointments on this slot
                slot.setNbPotentialRemainingPlaces( nNbMaxCapacity );
                slot.setNbRemainingPlaces( nNbMaxCapacity );
            }
        }
        saveSlot( slot );
        createListSlot( listSlotToCreate );
    }

    /**
     * Save a slot in database
     * 
     * @param slot
     *            the slot to save
     * @return the slot saved
     */
    public static Slot saveSlot( Slot slot )
    {
        Slot slotSaved = null;
        if ( slot.getIdSlot( ) == 0 )
        {
            slotSaved = SlotService.createSlot( slot );
        }
        else
        {
            slotSaved = SlotService.updateSlot( slot );
        }
        return slotSaved;
    }

    /**
     * Update a slot
     * 
     * @param slot
     *            the slot updated
     */
    public static Slot updateSlot( Slot slot )
    {
        SlotListenerManager.notifyListenersSlotChange( slot.getIdSlot( ) );
        return SlotHome.update( slot );
    }

    /**
     * Generate the list of slot to create after a slot (taking into account the week definition and the rules to apply)
     * 
     * @param slot
     *            the slot
     * @return the list of next slots
     */
    private static List<Slot> generateListSlotToCreateAfterATime( LocalDateTime dateTimeToStartCreation, int nIdForm )
    {
        List<Slot> listSlotToCreate = new ArrayList<>( );
        LocalDate dateOfCreation = dateTimeToStartCreation.toLocalDate( );
        ReservationRule reservationRule = ReservationRuleService.findReservationRuleByIdFormAndClosestToDateOfApply( nIdForm, dateOfCreation );
        int nMaxCapacity = reservationRule.getMaxCapacityPerSlot( );
        WeekDefinition weekDefinition = WeekDefinitionService.findWeekDefinitionByIdFormAndClosestToDateOfApply( nIdForm, dateOfCreation );
        WorkingDay workingDay = WorkingDayService.getWorkingDayOfDayOfWeek( weekDefinition.getListWorkingDay( ), dateOfCreation.getDayOfWeek( ) );
        LocalTime endingTimeOfTheDay = null;
        List<TimeSlot> listTimeSlot = new ArrayList<>( );
        int nDurationSlot = 0;
        if ( workingDay != null )
        {
            endingTimeOfTheDay = WorkingDayService.getMaxEndingTimeOfAWorkingDay( workingDay );
            nDurationSlot = WorkingDayService.getMinDurationTimeSlotOfAWorkingDay( workingDay );
            listTimeSlot = TimeSlotService.findListTimeSlotByWorkingDay( workingDay.getIdWorkingDay( ) );
        }
        else
        {
            endingTimeOfTheDay = WorkingDayService.getMaxEndingTimeOfAListOfWorkingDay( weekDefinition.getListWorkingDay( ) );
            nDurationSlot = WorkingDayService.getMinDurationTimeSlotOfAListOfWorkingDay( weekDefinition.getListWorkingDay( ) );
        }
        LocalDateTime endingDateTimeOfTheDay = endingTimeOfTheDay.atDate( dateOfCreation );
        LocalDateTime startingDateTime = dateTimeToStartCreation;
        LocalDateTime endingDateTime = startingDateTime.plusMinutes( nDurationSlot );
        while ( !endingDateTime.isAfter( endingDateTimeOfTheDay ) )
        {
            Slot slotToCreate = buildSlot( nIdForm, new Period( startingDateTime, endingDateTime ), nMaxCapacity, nMaxCapacity, nMaxCapacity, Boolean.FALSE,
                    Boolean.TRUE );
            slotToCreate.setIsSpecific( isSpecificSlot( slotToCreate, workingDay, listTimeSlot ) );
            startingDateTime = endingDateTime;
            endingDateTime = startingDateTime.plusMinutes( nDurationSlot );
            listSlotToCreate.add( slotToCreate );
        }
        if ( startingDateTime.isBefore( endingDateTimeOfTheDay ) && endingDateTime.isAfter( endingDateTimeOfTheDay ) )
        {
            Slot slotToCreate = buildSlot( nIdForm, new Period( startingDateTime, endingDateTimeOfTheDay ), nMaxCapacity, nMaxCapacity, nMaxCapacity,
                    Boolean.FALSE, Boolean.TRUE );
            slotToCreate.setIsSpecific( isSpecificSlot( slotToCreate, workingDay, listTimeSlot ) );
            listSlotToCreate.add( slotToCreate );
        }
        return listSlotToCreate;
    }

    /**
     * Form the DTO, adding the date and the time to the slot
     * 
     * @param slot
     *            the slot on which to add values
     */
    public static void addDateAndTimeToSlot( Slot slot )
    {
        if ( slot.getStartingDateTime( ) != null )
        {
            slot.setDate( slot.getStartingDateTime( ).toLocalDate( ) );
            slot.setStartingTime( slot.getStartingDateTime( ).toLocalTime( ) );
        }
        if ( slot.getEndingDateTime( ) != null )
        {
            slot.setEndingTime( slot.getEndingDateTime( ).toLocalTime( ) );
        }
    }

    /**
     * Create in database the slots given
     * 
     * @param listSlotToCreate
     *            the list of slots to create in database
     */
    private static void createListSlot( List<Slot> listSlotToCreate )
    {
        if ( CollectionUtils.isNotEmpty( listSlotToCreate ) )
        {
            for ( Slot slotTemp : listSlotToCreate )
            {
                SlotService.createSlot( slotTemp );
            }
        }
    }

    public static Slot createSlot( Slot slot )
    {
        Slot slotCreated = SlotHome.create( slot );
        SlotListenerManager.notifyListenersSlotCreation( slot.getIdSlot( ) );
        return slotCreated;
    }

    /**
     * Delete a list of slots
     * 
     * @param listSlotToDelete
     *            the lost of slots to delete
     */
    private static void deleteListSlot( List<Slot> listSlotToDelete )
    {
        for ( Slot slotToDelete : listSlotToDelete )
        {
            SlotService.deleteSlot( slotToDelete );
        }
    }

    /**
     * Delete a slot
     * 
     * @param slot
     *            the slot to delete
     */
    public static void deleteSlot( Slot slot )
    {
        SlotListenerManager.notifyListenersSlotRemoval( slot.getIdSlot( ) );
        SlotHome.delete( slot.getIdSlot( ) );
    }

    /**
     * Find the first open slot with free places
     * 
     * @param nIdForm
     *            the form Id
     * @param startingDate
     *            the starting date to search
     * @param endingDate
     *            the ending date to search
     * @return the date of the slot found
     */
    public static LocalDate findFirstDateOfFreeOpenSlot( int nIdForm, LocalDate startingDate, LocalDate endingDate )
    {
        boolean bFreeSlotFound = false;
        LocalDate localDateFound = null;
        LocalDate currentDateOfSearch = startingDate;
        List<LocalDate> listClosingDate = ClosingDayService.findListDateOfClosingDayByIdFormAndDateRange( nIdForm, startingDate, endingDate );
        // Get all the slot between these two dates
        HashMap<LocalDateTime, Slot> mapSlot = SlotService.findSlotsByIdFormAndDateRange( nIdForm, startingDate.atStartOfDay( ),
                endingDate.atTime( LocalTime.MAX ) );
        while ( !bFreeSlotFound && ( currentDateOfSearch.isBefore( endingDate ) || !currentDateOfSearch.isAfter( endingDate ) ) )
        {
            if ( !listClosingDate.contains( currentDateOfSearch ) )
            {
                WeekDefinition weekDefinition = WeekDefinitionService.findWeekDefinitionByIdFormAndClosestToDateOfApply( nIdForm, currentDateOfSearch );
                WorkingDay workingDay = WorkingDayService.getWorkingDayOfDayOfWeek( weekDefinition.getListWorkingDay( ), currentDateOfSearch.getDayOfWeek( ) );
                if ( workingDay != null )
                {
                    LocalTime minTimeForThisDay = WorkingDayService.getMinStartingTimeOfAWorkingDay( workingDay );
                    LocalTime maxTimeForThisDay = WorkingDayService.getMaxEndingTimeOfAWorkingDay( workingDay );
                    LocalTime timeTemp = minTimeForThisDay;
                    // For each slot of this day
                    while ( timeTemp.isBefore( maxTimeForThisDay ) || !timeTemp.equals( maxTimeForThisDay ) )
                    {
                        // Get the LocalDateTime
                        LocalDateTime dateTimeTemp = currentDateOfSearch.atTime( timeTemp );
                        // Search if there is a slot for this datetime
                        if ( mapSlot.containsKey( dateTimeTemp ) )
                        {
                            Slot slot = mapSlot.get( dateTimeTemp );
                            if ( slot.getIsOpen( ) && slot.getNbRemainingPlaces( ) > 0 )
                            {
                                bFreeSlotFound = true;
                                localDateFound = currentDateOfSearch;
                                break;
                            }
                            else
                            {
                                timeTemp = slot.getEndingDateTime( ).toLocalTime( );
                            }
                        }
                        else
                        {
                            // Search the timeslot
                            TimeSlot timeSlot = TimeSlotService.getTimeSlotInListOfTimeSlotWithStartingTime( workingDay.getListTimeSlot( ), timeTemp );
                            if ( timeSlot != null )
                            {
                                if ( timeSlot.getIsOpen( ) && timeSlot.getMaxCapacity( ) > 0 )
                                {
                                    bFreeSlotFound = true;
                                    localDateFound = currentDateOfSearch;
                                    break;
                                }
                                else
                                {
                                    timeTemp = timeSlot.getEndingTime( );
                                }
                            }
                            else
                            {
                                break;
                            }
                        }
                    }
                }
            }
            currentDateOfSearch = currentDateOfSearch.plusDays( 1 );
        }
        return localDateFound;
    }

}

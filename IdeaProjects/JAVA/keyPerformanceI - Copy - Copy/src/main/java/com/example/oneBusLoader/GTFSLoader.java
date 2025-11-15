package com.example.oneBusLoader;

import org.onebusaway.gtfs.model.*;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.serialization.GtfsReader;

import java.util.*;
import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;


public class GTFSLoader {
    public GtfsDaoImpl dao;
    private LocalDateTime date;

    public GTFSLoader(String path, LocalDateTime travelDataTime) throws Exception{
        this.date = travelDataTime;
        dao = new GtfsDaoImpl();

        GtfsReader reader = new GtfsReader();
        reader.setInputLocation(new File(path));
        reader.setEntityStore(dao);
        reader.run();
    }

    public Collection<StopTime> getAllStopTimes() {return dao.getAllStopTimes();}

    public Collection<Stop> getAllStops(){
        return dao.getAllStops();
    }

    public List<Trip> getActiveTrips() {
        List<Trip> activeTrips = new ArrayList<>();

        // Build lookup maps for calendars and exceptions
        Map<AgencyAndId, ServiceCalendar> calendarByServiceId = new HashMap<>();
        for (ServiceCalendar cal : dao.getAllCalendars()) {
            calendarByServiceId.put(cal.getServiceId(), cal);
        }

        Map<AgencyAndId, List<ServiceCalendarDate>> calendarDatesByServiceId = new HashMap<>();
        for (ServiceCalendarDate cd : dao.getAllCalendarDates()) {
            calendarDatesByServiceId
                    .computeIfAbsent(cd.getServiceId(), k -> new ArrayList<>())
                    .add(cd);
        }

        // Extract date + time
        LocalDate targetDate = date.toLocalDate();
        LocalTime targetTime = date.toLocalTime();
        DayOfWeek dow = targetDate.getDayOfWeek();

        // Iterate over all trips
        for (Trip trip : dao.getAllTrips()) {
            AgencyAndId serviceId = trip.getServiceId();
            boolean isActive = false;

            // calendar_dates.txt exceptions
            if (calendarDatesByServiceId.containsKey(serviceId)) {
                for (ServiceCalendarDate cd : calendarDatesByServiceId.get(serviceId)) {
                    LocalDate cdDate = LocalDate.of(cd.getDate().getYear(), cd.getDate().getMonth(), cd.getDate().getDay());
                    if (cdDate.equals(targetDate)) {
                        isActive = cd.getExceptionType() == ServiceCalendarDate.EXCEPTION_TYPE_ADD;
                        break;
                    }
                }
            }

            // calendar.txt if no exception
            if (!isActive && calendarByServiceId.containsKey(serviceId)) {
                ServiceCalendar cal = calendarByServiceId.get(serviceId);
                LocalDate start = LocalDate.of(cal.getStartDate().getYear(), cal.getStartDate().getMonth(), cal.getStartDate().getDay());
                LocalDate end = LocalDate.of(cal.getEndDate().getYear(),   cal.getEndDate().getMonth(),   cal.getEndDate().getDay());

                if (!targetDate.isBefore(start) && !targetDate.isAfter(end)) {
                    switch (dow) {
                        case MONDAY: isActive = cal.getMonday() == 1; break;
                        case TUESDAY: isActive = cal.getTuesday() == 1; break;
                        case WEDNESDAY: isActive = cal.getWednesday() == 1; break;
                        case THURSDAY: isActive = cal.getThursday() == 1; break;
                        case FRIDAY: isActive = cal.getFriday() == 1; break;
                        case SATURDAY: isActive = cal.getSaturday() == 1; break;
                        case SUNDAY: isActive = cal.getSunday() == 1; break;
                    }
                }
            }

            // New: keep only trips with a stop after targetTime
            if (isActive) {
                List<StopTime> stopTimes = getStopTimesForTrip(trip);

                boolean validAfterTime = stopTimes.stream()
                        .anyMatch(st -> {
                            int secs = st.getDepartureTime(); // GTFS: seconds since midnight, can be > 86400
                            return secs >= targetTime.toSecondOfDay();
                        });

                if (validAfterTime) {
                    activeTrips.add(trip);
                }
            }
        }

        System.out.println("Total trips: " + dao.getAllTrips().size());
        System.out.println("Active trips on " + targetDate + " after " + targetTime + ": " + activeTrips.size());
        return activeTrips;
    }

    public List<StopTime> getStopTimesForTrip(Trip trip) {
        List<StopTime> stopTimes = new ArrayList<>();

        // loop over all stop_times
        for (StopTime st : dao.getAllStopTimes()) {
            if (st.getTrip().equals(trip)) {
                stopTimes.add(st);
            }
        }

        // sort by stop_sequence to ensure travel order
        stopTimes.sort(Comparator.comparingInt(StopTime::getStopSequence));
        return stopTimes;
    }
}

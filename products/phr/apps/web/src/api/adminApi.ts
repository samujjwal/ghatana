// Re-export appointment-related functions from phrApiCore
export {
  bookAppointment,
  cancelAppointment,
  createAppointmentRequest,
  fetchAppointments,
  rescheduleAppointment,
} from './phrApiCore';

// Provider, Caregiver, FCHV functions removed - routes are hidden in route contract
// and not yet implemented. See API-004 in implementation tracker.

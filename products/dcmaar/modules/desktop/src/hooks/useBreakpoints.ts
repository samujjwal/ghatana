import { useEffect, useState } from 'react';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';

/**
 * Breakpoint values from the theme
 */
interface Breakpoints {
  isXs: boolean;
  isSm: boolean;
  isMd: boolean;
  isLg: boolean;
  isXl: boolean;
  isUp: (key: 'xs' | 'sm' | 'md' | 'lg' | 'xl') => boolean;
  isDown: (key: 'xs' | 'sm' | 'md' | 'lg' | 'xl') => boolean;
  isBetween: (start: 'xs' | 'sm' | 'md' | 'lg' | 'xl', end: 'xs' | 'sm' | 'md' | 'lg' | 'xl') => boolean;
}

/**
 * Custom hook to get the current breakpoint
 * @returns {Breakpoints} Object with breakpoint information
 */
const useBreakpoints = (): Breakpoints => {
  const theme = useTheme();
  
  const [breakpoints, setBreakpoints] = useState<Omit<Breakpoints, 'isUp' | 'isDown' | 'isBetween'>>({
    isXs: false,
    isSm: false,
    isMd: false,
    isLg: false,
    isXl: false,
  });
  
  // Check each breakpoint
  const isXs = useMediaQuery(theme.breakpoints.only('xs'));
  const isSm = useMediaQuery(theme.breakpoints.only('sm'));
  const isMd = useMediaQuery(theme.breakpoints.only('md'));
  const isLg = useMediaQuery(theme.breakpoints.only('lg'));
  const isXl = useMediaQuery(theme.breakpoints.only('xl'));
  
  // Update breakpoints when they change
  useEffect(() => {
    setBreakpoints({
      isXs,
      isSm,
      isMd,
      isLg,
      isXl,
    });
  }, [isXs, isSm, isMd, isLg, isXl]);
  
  // Check if the current breakpoint is greater than or equal to the given breakpoint
  const isUp = (key: 'xs' | 'sm' | 'md' | 'lg' | 'xl'): boolean => {
    const breakpointOrder = ['xs', 'sm', 'md', 'lg', 'xl'];
    const currentBreakpoint = breakpointOrder.findIndex(bp => 
      breakpoints[`is${bp.toUpperCase()}` as keyof typeof breakpoints]
    );
    const targetBreakpoint = breakpointOrder.indexOf(key);
    
    return currentBreakpoint >= targetBreakpoint;
  };
  
  // Check if the current breakpoint is less than or equal to the given breakpoint
  const isDown = (key: 'xs' | 'sm' | 'md' | 'lg' | 'xl'): boolean => {
    const breakpointOrder = ['xs', 'sm', 'md', 'lg', 'xl'];
    const currentBreakpoint = breakpointOrder.findIndex(bp => 
      breakpoints[`is${bp.toUpperCase()}` as keyof typeof breakpoints]
    );
    const targetBreakpoint = breakpointOrder.indexOf(key);
    
    return currentBreakpoint <= targetBreakpoint;
  };
  
  // Check if the current breakpoint is between two breakpoints (inclusive)
  const isBetween = (
    start: 'xs' | 'sm' | 'md' | 'lg' | 'xl',
    end: 'xs' | 'sm' | 'md' | 'lg' | 'xl'
  ): boolean => {
    return isUp(start) && isDown(end);
  };
  
  return {
    ...breakpoints,
    isUp,
    isDown,
    isBetween,
  };
};

export default useBreakpoints;

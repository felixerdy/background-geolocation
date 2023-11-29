export interface WatcherOptions {
    backgroundMessage?: string;
    backgroundTitle?: string;
    requestPermissions?: boolean;
    stale?: boolean;
    distanceFilter?: number;
}

export interface Location {
    latitude: number;
    longitude: number;
    accuracy: number;
    altitude: number | null;
    altitudeAccuracy: number | null;
    simulated: boolean;
    bearing: number | null;
    speed: number | null;
    time: number | null;
}

export interface CallbackError extends Error {
    code?: string;
}

export interface SIMPORTBackgroundGeolocationPlugin {
    addWatcher(
        options: WatcherOptions,
        callback: (
            position?: Location,
            error?: CallbackError
        ) => void
    ): Promise<string>;
    removeWatcher(options: {
        id: string
    }): Promise<void>;
    openSettings(): Promise<void>;
    processLocation(options: { location: Location }): Promise<{location: Location}>
}

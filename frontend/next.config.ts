import type { NextConfig } from "next";

const apiServiceUrl =
  process.env.API_SERVICE_URL ?? "http://localhost:8082";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${apiServiceUrl}/:path*`,
      },
    ];
  },
};

export default nextConfig;

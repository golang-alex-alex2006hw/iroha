/**
 * Copyright Soramitsu Co., Ltd. 2017 All Rights Reserved.
 * http://soramitsu.co.jp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

///
/// Documentation is at https://github.com/google/benchmark
///
/// General recommendations:
///  - build with -DCMAKE_BUILD_TYPE=Release
///  - disable CPU scaling (frequency changes depending on workload)
///  - put initialization code in fixtures
///  - write meaningful names for benchmarks
///

#include <benchmark/benchmark.h>
#include <builders/default_builders.hpp>
#include <string>

#include "builders/protobuf/transaction.hpp"
#include "validators/default_validator.hpp"
#include "cryptography/crypto_provider/crypto_defaults.hpp"

/// Test how long is empty std::string creation
// define a static function, which accepts 'state'
// function's name = benchmark's name
static void BM_FieldValidator(benchmark::State &state) {
  const shared_model::crypto::Keypair keypair =
      shared_model::crypto::DefaultCryptoAlgorithmType::generateKeypair();

  shared_model::proto::TransactionBuilder builder;

  auto tx = builder.creatorAccountId("hello@test")
                .createdTime(iroha::time::now())
                .addAssetQuantity("hello@test", "us#dollar", "2.50")
                .build().signAndAddSignature(keypair);
  // define main benchmark loop
  while (state.KeepRunning()) {
    auto answer =
        shared_model::validation::DefaultTransactionValidator().validate(tx);
  }
}
// define benchmark
BENCHMARK(BM_FieldValidator);

/// That's all. More in documentation.

// don't forget to include this:
BENCHMARK_MAIN();
